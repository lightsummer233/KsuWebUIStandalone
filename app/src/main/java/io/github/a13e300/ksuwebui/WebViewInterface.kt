package io.github.a13e300.ksuwebui

import android.content.pm.ApplicationInfo
import android.text.TextUtils
import android.webkit.JavascriptInterface
import androidx.compose.runtime.Stable
import androidx.core.content.pm.PackageInfoCompat
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
            "(function() { try { ${callbackFunc}(${result.code}, ${JSONObject.quote(stdout)}, ${
                JSONObject.quote(stderr)
            }); } catch(e) { console.error(e); } })();"

        viewModel.sendEvent(WebViewEvent.EvaluateJavascript(jsCode))
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
                "(function() { try { ${callbackFunc}.${name}.emit('data', ${JSONObject.quote(data)}); } catch(e) { console.error('emitData', e); } })();"

            viewModel.sendEvent(WebViewEvent.EvaluateJavascript(jsCode))
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
                $$"(function() { try { $${callbackFunc}.emit('exit', $${result.code}); } catch(e) { console.error(`emitExit error: ${e}`); } })();"

            viewModel.sendEvent(WebViewEvent.EvaluateJavascript(emitExitCode))

            if (result.code != 0) {
                val emitErrCode =
                    "(function() { try { var err = new Error(); err.exitCode = ${result.code}; err.message = ${
                        JSONObject.quote(result.err.joinToString("\n"))
                    };${callbackFunc}.emit('error', err); } catch(e) { console.error('emitErr', e); } })();"

                viewModel.sendEvent(WebViewEvent.EvaluateJavascript(emitErrCode))
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
        // TODO
        return ""
    }

    @JavascriptInterface
    override fun listPackages(type: String): String {
        val packageNames = viewModel.packageInfoList.value
            .filter { packageInfo ->
                val flags = packageInfo.applicationInfo?.flags ?: 0
                when (type.lowercase()) {
                    "system" -> (flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    "user" -> (flags and ApplicationInfo.FLAG_SYSTEM) == 0
                    else -> true
                }
            }
            .map { it.packageName }
            .sorted()

        val jsonArray = JSONArray()
        for (packageName in packageNames) {
            jsonArray.put(packageName)
        }
        return jsonArray.toString()
    }

    @JavascriptInterface
    override fun getPackagesInfo(packageNamesJson: String): String {
        val packageNames = JSONArray(packageNamesJson)
        val jsonArray = JSONArray()

        val pm = App.packageManager

        val appMap = viewModel.packageInfoList.value.associateBy { it.packageName }
        for (i in 0 until packageNames.length()) {
            val packageName = packageNames.getString(i)
            val packageInfo = appMap[packageName]
            if (packageInfo != null) {
                val applicationInfo = packageInfo.applicationInfo

                val obj = JSONObject()
                obj.put("packageName", packageInfo.packageName)
                obj.put("versionName", packageInfo.versionName ?: "")
                obj.put("versionCode", PackageInfoCompat.getLongVersionCode(packageInfo))
                obj.put("appLabel", applicationInfo?.loadLabel(pm) ?: JSONObject.NULL)
                obj.put("isSystem", applicationInfo?.let { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 } ?: JSONObject.NULL)
                obj.put("uid", applicationInfo?.uid ?: JSONObject.NULL)
                jsonArray.put(obj)
            } else {
                val obj = JSONObject()
                obj.put("packageName", packageName)
                obj.put("error", "Package not found or inaccessible")
                jsonArray.put(obj)
            }
        }
        return jsonArray.toString()
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
