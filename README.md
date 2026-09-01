# JSON Tools

A Spring Boot MVC application with Thymeleaf views offering the tool set found on
jsonformatter.org: formatting, validation, conversion between JSON/XML/YAML/CSV,
document comparison and model-class generation.

- **Spring Boot 3.5.5**, **Java 21**, packaged as an executable JAR
- **Thymeleaf** views under `resources/templates`
- 41 tools, all driven from one registry and one generic page

## Running

```bash
./mvnw spring-boot:run
```

Then open <http://localhost:8080>.

To build a deployable artifact:

```bash
./mvnw clean package
java -jar target/json-formatter.jar
```

The JAR is self-contained (embedded Tomcat); no external servlet container is
involved. Change the port with `--server.port=…` and the context path with
`--server.servlet.context-path=/tools`.

### Toolchain requirements

| Tool | Minimum | Why |
|------|---------|-----|
| JDK | 21 | `java.version` in the POM |
| Maven | 3.6.3+ | required by `spring-boot-maven-plugin` 3.x |

A Maven wrapper is committed, so `./mvnw` fetches a supported Maven itself and
only a JDK 21 needs to be on `PATH` / `JAVA_HOME`.

The source uses no language feature newer than Java 17, so the build can be run
on a JDK 17 for a quick check by overriding the release:

```bash
./mvnw -Djava.version=17 clean test
```

## The tools

| Group | Tools |
|-------|-------|
| Format & Beautify | JSON Formatter, Viewer (tree), Editor, Minify, Sorter, Fixer, XML Formatter, XML Minify, YAML Formatter |
| Validate & Query | JSON / XML / YAML Validator, JSON Schema Validator, JSON Schema Generator, JSONPath Tester, JSON Diff, JSON Merge |
| Convert | JSON ↔ XML, JSON ↔ YAML, JSON ↔ CSV, JSON ↔ TSV, JSON → HTML table, JSON → Excel, JSON → SQL, JSON ↔ escaped string |
| Generate Code | JSON → Java (classes or records), TypeScript, C#, Python, Go |
| Text Utilities | JSON Escape / Unescape, Base64 Encode / Decode, URL Encode / Decode |

## How it is put together

```
model/       Tool, ToolOption, ToolResult (view models), TypeNode (inferred shape)
registry/    ToolRegistry - the catalogue; one entry per tool
service/     One service per concern, plus ToolExecutor which dispatches by tool id
web/         HomeController (pages), ApiController (JSON API)
resources/templates  Thymeleaf views - layout, index, not-found, the generic tool page
resources/static     CSS and the progressive-enhancement JavaScript
```

Adding a tool is a `ToolRegistry` entry plus a branch in `ToolExecutor`; the
page, the navigation and the option controls render themselves from the
registry entry.

### Pages work without JavaScript

Every tool page is a plain form that POSTs to `/{toolId}` and is re-rendered by
the server. `app.js` upgrades that to in-place updates through the API, and adds
the collapsible tree view, the sandboxed HTML preview and client-side downloads.

### API

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/api/tool/{toolId}` | run a tool: `{input, secondInput, options}` → `{ok, output, error, line, column, stats, details}` |
| POST | `/api/excel` | build the .xlsx workbook for JSON → Excel |
| POST | `/api/upload` | read an uploaded file into a pane |
| POST | `/api/fetch` | load a document from a public URL |
| GET | `/api/tools` | the tool catalogue |

## Notes on the risky bits

- **XML parsing** disables DTDs and external entity resolution, so pasting
  untrusted XML cannot trigger XXE.
- **`/api/fetch`** is a deliberate server-side-request surface. It allows only
  http/https, resolves each host and refuses loopback, link-local, site-local,
  any-local and multicast addresses, follows at most three redirects while
  re-checking every hop, and caps the response at 5 MB.
- **The HTML preview** renders generated markup in an `<iframe sandbox="">`, so
  a document containing `<script>` cannot execute in the page.
- **Numbers** are parsed as `BigDecimal` throughout, so `1.20` and integers
  wider than a `long` survive a round trip unchanged.

## Tests

```bash
./mvnw test
```

100 tests: a parameterized smoke test running every registered tool against its
own sample, per-tool behaviour tests for the conversions and generators, and
MockMvc tests covering the pages, the API and the SSRF guards. The page tests
render the templates for real, so a broken expression fails the build.
