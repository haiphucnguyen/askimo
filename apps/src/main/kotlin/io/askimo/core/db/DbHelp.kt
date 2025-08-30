/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.db

object DbHelp {
    val SHORT =
        """
        Database commands
          :db add            Add/save a connection (wizard or flags)
          :db use            Attach a saved/ephemeral connection to this session
          :db off            Detach the current connection
          :db list           List saved connections
          :db show           Show connection details (secrets redacted)
          :db test           Ping + list schemas
          :db remove         Delete a saved connection
          :db rotate-secret  Update only the secret

        Tip: :db help              for all details
             :db <sub> help        for detailed help on a subcommand
        """.trimIndent()

    val ALL =
        """
        Usage:
          :db <use|off|list|add|show|test|remove|rotate-secret> [options]

        add — create and save a named connection.
          :db add
          :db add --id <ID> --url <JDBC_URL> --user <USER>
                  [--pass-env <NAME> | --pass-file <PATH> | --pass-prompt]
                  [--engine <postgres|mysql|sqlserver|sqlite>]
                  [--read-only | --no-read-only]
                  [--max-rows <N>] [--timeout-sec <N>]
          Example:
            :db add --id flow --url jdbc:postgresql://localhost:5432/flowinquiry \
                    --user flowinquiry --pass-prompt --read-only --max-rows 100 --timeout-sec 10

        use — attach a connection to this REPL session and start DB tools.
          Saved:      :db use <ID> [--read-only|--no-read-only] [--max-rows <N>] [--timeout-sec <N>]
          Ephemeral:  :db use --url <JDBC_URL> --user <USER>
                               [--pass-env <NAME>|--pass-file <PATH>|--pass-prompt]
                               [--engine <postgres|mysql|sqlserver|sqlite>]
                               [--read-only|--no-read-only] [--max-rows <N>] [--timeout-sec <N>]
          Examples:
            :db use flow
            :db use flow --max-rows 1000 --timeout-sec 30
            :db use --url jdbc:postgresql://db:5432/app --user app --pass-env APP_DB_PW

        off — detach the current connection (stops the DB process).
          :db off

        list — show saved connections.
          :db list
          :db list --verbose

        show — show details (secrets redacted).
          :db show <ID>

        test — ping + list schemas using a temp process.
          :db test <ID>
          Example:
            :db test flow  → ✅ ping=ok, schemas=[public, pg_catalog, information_schema] …

        remove — delete a saved connection (optionally forget the stored secret).
          :db remove <ID> [--forget-secret]

        rotate-secret — update only the secret for an existing connection.
          :db rotate-secret <ID> (--pass-env <NAME> | --pass-file <PATH> | --pass-prompt)

        Common options:
          --read-only | --no-read-only   (default: --read-only)
          --max-rows <N>                 (default: 100)
          --timeout-sec <N>              (default: 10)
        """.trimIndent()

    // detailed help per subcommand (used for ":db <sub> help" and on bad args)
    private val PER_SUB =
        mapOf(
            "add" to
                """
                add — create and save a named connection.

                Usage:
                  :db add
                  :db add --id <ID> --url <JDBC_URL> --user <USER>
                          [--pass-env <NAME> | --pass-file <PATH> | --pass-prompt]
                          [--engine <postgres|mysql|sqlserver|sqlite>]
                          [--read-only | --no-read-only]
                          [--max-rows <N>] [--timeout-sec <N>]

                Notes:
                  • Engine is inferred from JDBC URL if omitted.
                  • Secrets are referenced (keychain/env/file), never stored in plaintext.
                  • Defaults: read-only, max-rows=100, timeout-sec=10.

                Example:
                  :db add --id flow --url jdbc:postgresql://localhost:5432/flowinquiry \
                          --user flowinquiry --pass-prompt --read-only --max-rows 100 --timeout-sec 10
                """.trimIndent(),
            "use" to
                """
                use — attach a connection to this session and start DB tools.

                Usage (saved):
                  :db use <ID> [--read-only | --no-read-only] [--max-rows <N>] [--timeout-sec <N>]

                Usage (ephemeral, not saved):
                  :db use --url <JDBC_URL> --user <USER>
                          [--pass-env <NAME> | --pass-file <PATH> | --pass-prompt]
                          [--engine <postgres|mysql|sqlserver|sqlite>]
                          [--read-only | --no-read-only]
                          [--max-rows <N>] [--timeout-sec <N>]

                Behavior:
                  • Detaches any currently attached DB first.
                  • Spawns askimo-mcp-db (stdio) and connects MCP.
                  • Read-only + limits apply; per-run overrides allowed.

                Examples:
                  :db use flow
                  :db use flow --max-rows 1000 --timeout-sec 30
                  :db use --url jdbc:postgresql://db:5432/app --user app --pass-env APP_DB_PW
                """.trimIndent(),
            "off" to
                """
                off — detach the current connection (stops the DB process).

                Usage:
                  :db off

                Notes:
                  • Safe to call when no DB is attached (no-op).
                """.trimIndent(),
            "list" to
                """
                list — show saved connections.

                Usage:
                  :db list
                  :db list --verbose

                Output:
                  * marks the currently attached connection.
                  --verbose includes redacted URL and username.

                Example:
                  :db list --verbose
                  * flow  POSTGRES  jdbc:postgresql://flowuser:***@localhost:5432/flowinquiry  user=flowinquiry ro=true rows=100 t=10s
                    ci    POSTGRES  jdbc:postgresql://db:5432/app                         user=app         ro=true rows=100 t=10s
                """.trimIndent(),
            "show" to
                """
                show — show details for a saved connection (secrets redacted).

                Usage:
                  :db show <ID>

                Example:
                  :db show flow
                  id        : flow
                  engine    : POSTGRES
                  url       : jdbc:postgresql://flowuser:***@localhost:5432/flowinquiry
                  user      : flowinquiry
                  secret    : keychain:askimo/flowinquiry
                  readOnly  : true
                  maxRows   : 100
                  timeoutSec: 10
                """.trimIndent(),
            "test" to
                """
                test — verify connectivity by pinging and listing schemas.

                Usage:
                  :db test <ID>

                Behavior:
                  • Spawns a temporary DB server process.
                  • Runs db.ping and db.listSchemas.
                  • Shuts down the process afterwards.

                Example:
                  :db test flow
                  ✅ ping=ok, schemas=[public, pg_catalog, information_schema] …
                """.trimIndent(),
            "remove" to
                """
                remove — delete a saved connection.

                Usage:
                  :db remove <ID> [--forget-secret]

                Notes:
                  • --forget-secret also deletes the stored secret reference (e.g., keychain entry).
                """.trimIndent(),
            "rotate-secret" to
                """
                rotate-secret — update only the secret for an existing connection.

                Usage:
                  :db rotate-secret <ID> (--pass-env <NAME> | --pass-file <PATH> | --pass-prompt)

                Examples:
                  :db rotate-secret flow --pass-prompt
                  :db rotate-secret flow --pass-env NEW_APP_DB_PW
                """.trimIndent(),
        )

    fun longFor(sub: String): String? = PER_SUB[sub]
}
