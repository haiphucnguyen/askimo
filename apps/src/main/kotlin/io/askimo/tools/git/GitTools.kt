package io.askimo.tools.git

import dev.langchain4j.agent.tool.Tool
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class GitTools {
    @Tool("Unified diff of staged changes (git diff --cached)")
    fun stagedDiff(args: List<String> = listOf("--no-color", "--unified=0")): String = exec(listOf("git","diff","--cached") + args)

    @Tool("Concise git status (-sb)")
    fun status(): String = exec(listOf("git","status","-sb"))

    @Tool("Current branch name")
    fun branch(): String = exec(listOf("git","rev-parse","--abbrev-ref","HEAD")).trim()

    @Tool("Write .git/COMMIT_EDITMSG and run git commit -F -")
    fun commit(message: String, signoff: Boolean = false, noVerify: Boolean = false, writeEditmsg: Boolean = true): String {
        if (writeEditmsg) IoTools.writeFile(".git/COMMIT_EDITMSG", message)
        val cmd = buildList {
            addAll(listOf("git","commit"))
            if (noVerify) add("--no-verify")
            if (signoff) add("--signoff")
            addAll(listOf("-F","-"))
        }
        return execWithStdin(cmd, message)
    }

    private fun exec(cmd: List<String>): String {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = ByteArrayOutputStream(); p.inputStream.copyTo(out); val code = p.waitFor()
        if (code != 0) error("Command failed: ${cmd.joinToString(" ")} ($code)")
        return out.toString(StandardCharsets.UTF_8)
    }
    private fun execWithStdin(cmd: List<String>, input: String): String {
        val p = ProcessBuilder(cmd).redirectErrorStream(true).start()
        p.outputStream.bufferedWriter().use { it.write(input) }
        val out = ByteArrayOutputStream(); p.inputStream.copyTo(out); val code = p.waitFor()
        if (code != 0) error("Command failed: ${cmd.joinToString(" ")} ($code)")
        return out.toString(StandardCharsets.UTF_8)
    }
}
