package io.github.a13e300.ksuwebui

import android.text.TextUtils
import android.webkit.JavascriptInterface
import androidx.compose.runtime.Stable
import com.topjohnwu.superuser.CallbackList
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ShellUtils
import com.topjohnwu.superuser.internal.UiThreadHandler
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CompletableFuture

@Stable
interface WebViewInterface {

    @JavascriptInterface
    fun exec(cmd: String): String

    @JavascriptInterface
    fun exec(cmd: String, callbackFunc: String)

    @JavascriptInterface
    fun exec(cmd: String, options: String?, callbackFunc: String)

    @JavascriptInterface
    fun spawn(command: String, args: String, options: String?, callbackFunc: String)

    @JavascriptInterface
    fun toast(msg: String)

    @JavascriptInterface
    fun fullScreen(enable: Boolean)

    @JavascriptInterface
    fun enableEdgeToEdge(enable: Boolean = true)

    @JavascriptInterface
    fun moduleInfo(): String

    @JavascriptInterface
    fun listPackages(type: String): String

    @JavascriptInterface
    fun getPackagesInfo(packageNamesJson: String): String

    @JavascriptInterface
    fun exit()
}

@Stable
class WebViewInterfaceImpl(
    private val viewModel: WebUIViewModel
) : WebViewInterface {

    @JavascriptInterface
    override fun exec(cmd: String): String {
        return withNewRootShell(true) { ShellUtils.fastCmd(this, cmd) }
    }

    @JavascriptInterface
    override fun exec(cmd: String, callbackFunc: String) {
        exec(cmd, null, callbackFunc)
    }

    @JavascriptInterface
    override fun exec(cmd: String, options: String?, callbackFunc: String) {
        val finalCommand = StringBuilder()
        processOptions(finalCommand, options)
        finalCommand.append(cmd)

        val result = withNewRootShell(true) {
            newJob().add(finalCommand.toString()).to(ArrayList(), ArrayList()).exec()
        }
        val stdout = result.out.joinToString(separator = "\n")
        val stderr = result.err.joinToString(separator = "\n")

        val jsCode =
            "javascript: (function() { try { ${callbackFunc}(${result.code}, ${
                JSONObject.quote(
                    stdout
                )
            }, ${JSONObject.quote(stderr)}); } catch(e) { console.error(e); } })();"

        viewModel.sendEvent(WebViewEvent.LoadUrl(jsCode))
    }

    @JavascriptInterface
    override fun spawn(
        command: String,
        args: String,
        options: String?,
        callbackFunc: String
    ) {
        val finalCommand = StringBuilder()

        processOptions(finalCommand, options)

        if (!TextUtils.isEmpty(args)) {
            finalCommand.append(command).append(" ")
            JSONArray(args).let { argsArray ->
                for (i in 0 until argsArray.length()) {
                    finalCommand.append(argsArray.getString(i))
                    finalCommand.append(" ")
                }
            }
        } else {
            finalCommand.append(command)
        }

        val shell = createRootShell(true)

        val emitData = fun(name: String, data: String) {
            val jsCode =
                "javascript: (function() { try { ${callbackFunc}.${name}.emit('data', ${
                    JSONObject.quote(
                        data
                    )
                }); } catch(e) { console.error('emitData', e); } })();"

            viewModel.sendEvent(WebViewEvent.LoadUrl(jsCode))
        }

        val stdout = object : CallbackList<String>(UiThreadHandler::runAndWait) {
            override fun onAddElement(s: String) {
                emitData("stdout", s)
            }
        }

        val stderr = object : CallbackList<String>(UiThreadHandler::runAndWait) {
            override fun onAddElement(s: String) {
                emitData("stderr", s)
            }
        }

        val future = shell.newJob().add(finalCommand.toString()).to(stdout, stderr).enqueue()
        val completableFuture = CompletableFuture.supplyAsync {
            future.get()
        }

        completableFuture.thenAccept { result ->
            val emitExitCode =
                "javascript: (function() { try { ${callbackFunc}.emit('exit', ${result.code}); } catch(e) { console.error(`emitExit error: \${e}`); } })();"

            viewModel.sendEvent(WebViewEvent.LoadUrl(emitExitCode))

            if (result.code != 0) {
                val emitErrCode =
                    "javascript: (function() { try { var err = new Error(); err.exitCode = ${result.code}; err.message = ${
                        JSONObject.quote(
                            result.err.joinToString(
                                "\n"
                            )
                        )
                    };${callbackFunc}.emit('error', err); } catch(e) { console.error('emitErr', e); } })();"

                viewModel.sendEvent(WebViewEvent.LoadUrl(emitErrCode))
            }
        }.whenComplete { _, _ ->
            val _ = runCatching { shell.close() }
        }
    }

    @JavascriptInterface
    override fun toast(msg: String) {
        viewModel.sendEvent(WebViewEvent.Toast(msg))
    }

    @JavascriptInterface
    override fun fullScreen(enable: Boolean) {
        viewModel.sendEvent(WebViewEvent.FullScreen(enable))
    }

    @JavascriptInterface
    override fun enableEdgeToEdge(enable: Boolean) {
        viewModel.sendEvent(WebViewEvent.EdgeToEdge(enable))
    }

    @JavascriptInterface
    override fun moduleInfo(): String {
        TODO("Not yet implemented")
    }

    @JavascriptInterface
    override fun listPackages(type: String): String {
        TODO("Not yet implemented")
    }

    @JavascriptInterface
    override fun getPackagesInfo(packageNamesJson: String): String {
        TODO("Not yet implemented")
    }

    @JavascriptInterface
    override fun exit() {
        viewModel.sendEvent(WebViewEvent.Exit)
    }

    inline fun <T> withNewRootShell(
        globalMnt: Boolean = false,
        block: Shell.() -> T
    ): T {
        return createRootShell(globalMnt).use(block)
    }

    fun createRootShell(globalMnt: Boolean = false): Shell {
        val builder = Shell.Builder.create()
        if (globalMnt) {
            builder.setFlags(Shell.FLAG_MOUNT_MASTER)
        }
        return builder.build()
    }

    private fun processOptions(sb: StringBuilder, options: String?) {
        val opts = if (options == null) JSONObject() else {
            JSONObject(options)
        }

        val cwd = opts.optString("cwd")
        if (!TextUtils.isEmpty(cwd)) {
            sb.append("cd ${cwd};")
        }

        opts.optJSONObject("env")?.let { env ->
            env.keys().forEach { key ->
                sb.append("export ${key}=${env.getString(key)};")
            }
        }
    }
}
