<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="layout/header.jsp" %>

<article class="tool"
         data-tool="${tool.id}"
         data-binary-download="${tool.binaryDownload}"
         data-download-name="<c:out value="${tool.downloadName}" />"
         data-output-view="${tool.outputSyntax}">

    <header class="tool-header">
        <h1><c:out value="${tool.title}" /></h1>
        <p class="tool-tagline"><c:out value="${tool.tagline}" /></p>
        <p class="tool-description"><c:out value="${tool.description}" /></p>
    </header>

    <form method="post" action="${ctx}/${tool.id}" id="tool-form" novalidate>

        <div class="option-bar">
            <c:forEach var="opt" items="${tool.options}">
                <c:set var="cur" value="${empty submitted ? opt.defaultValue : submitted[opt.key]}" />
                <c:choose>
                    <c:when test="${opt.select}">
                        <div class="option">
                            <label for="opt-${opt.key}"><c:out value="${opt.label}" /></label>
                            <select id="opt-${opt.key}" name="${opt.key}">
                                <c:forEach var="ch" items="${opt.choices}">
                                    <option value="<c:out value='${ch.value}'/>"
                                            <c:if test="${cur eq ch.value}">selected</c:if>>
                                        <c:out value="${ch.label}" />
                                    </option>
                                </c:forEach>
                            </select>
                        </div>
                    </c:when>
                    <c:when test="${opt.toggle}">
                        <div class="option option-check">
                            <input type="checkbox" id="opt-${opt.key}" name="${opt.key}" value="true"
                                   <c:choose>
                                       <c:when test="${empty submitted}"><c:if test="${opt.checked}">checked</c:if></c:when>
                                       <c:otherwise><c:if test="${cur eq 'true'}">checked</c:if></c:otherwise>
                                   </c:choose>>
                            <label for="opt-${opt.key}"><c:out value="${opt.label}" /></label>
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="option">
                            <label for="opt-${opt.key}"><c:out value="${opt.label}" /></label>
                            <input type="text" id="opt-${opt.key}" name="${opt.key}"
                                   value="<c:out value='${cur}'/>"
                                   placeholder="<c:out value='${opt.placeholder}'/>"
                                   spellcheck="false" autocomplete="off">
                        </div>
                    </c:otherwise>
                </c:choose>
            </c:forEach>

            <div class="option-actions">
                <button type="submit" class="button button-primary" id="run-button">
                    <c:out value="${tool.actionLabel}" />
                </button>
            </div>
        </div>

        <div class="panes ${tool.dualInput ? 'panes-three' : 'panes-two'}">

            <section class="pane">
                <header class="pane-header">
                    <h2><c:out value="${tool.inputLabel}" /></h2>
                    <div class="pane-tools">
                        <button type="button" class="link-button" data-action="sample">Sample</button>
                        <button type="button" class="link-button" data-action="upload" data-target="input">Upload</button>
                        <button type="button" class="link-button" data-action="url" data-target="input">URL</button>
                        <button type="button" class="link-button" data-action="clear" data-target="input">Clear</button>
                    </div>
                </header>
                <textarea id="input" name="input" class="editor" spellcheck="false"
                          autocapitalize="off" autocorrect="off"
                          placeholder="Paste your <c:out value='${tool.inputSyntax}'/> here&hellip;"><c:out value="${input}" /></textarea>
                <p class="pane-status" id="input-status"></p>
            </section>

            <c:if test="${tool.dualInput}">
                <section class="pane">
                    <header class="pane-header">
                        <h2><c:out value="${tool.secondInputLabel}" /></h2>
                        <div class="pane-tools">
                            <button type="button" class="link-button" data-action="sample2">Sample</button>
                            <button type="button" class="link-button" data-action="upload" data-target="secondInput">Upload</button>
                            <button type="button" class="link-button" data-action="url" data-target="secondInput">URL</button>
                            <button type="button" class="link-button" data-action="clear" data-target="secondInput">Clear</button>
                        </div>
                    </header>
                    <textarea id="secondInput" name="secondInput" class="editor" spellcheck="false"
                              autocapitalize="off" autocorrect="off"><c:out value="${secondInput}" /></textarea>
                </section>
            </c:if>

            <section class="pane pane-output">
                <header class="pane-header">
                    <h2><c:out value="${tool.outputLabel}" /></h2>
                    <div class="pane-tools">
                        <c:if test="${tool.tableOutput}">
                            <button type="button" class="link-button" data-action="toggle-preview" aria-pressed="false">Preview</button>
                        </c:if>
                        <button type="button" class="link-button" data-action="copy">Copy</button>
                        <c:if test="${tool.downloadable}">
                            <button type="button" class="link-button" data-action="download">Download</button>
                        </c:if>
                    </div>
                </header>

                <div class="output-area" id="output-area">

                    <div class="alert alert-error" id="error-box"
                         <c:if test="${empty result or result.ok}">hidden</c:if>>
                        <strong id="error-message"><c:out value="${result.error}" /></strong>
                        <c:if test="${not empty result and result.positioned}">
                            <span class="error-position" id="error-position">
                                line <c:out value="${result.line}" />, column <c:out value="${result.column}" />
                            </span>
                        </c:if>
                    </div>

                    <c:if test="${tool.reportOutput}">
                        <div class="alert alert-ok" id="ok-box"
                             <c:if test="${empty result or not result.ok}">hidden</c:if>>
                            <strong id="ok-message"><c:out value="${result.output}" /></strong>
                        </div>
                    </c:if>

                    <textarea id="output" class="editor editor-output" readonly spellcheck="false"
                              <c:if test="${tool.reportOutput}">hidden</c:if>><c:out value="${result.output}" /></textarea>

                    <c:if test="${tool.treeOutput}">
                        <div class="tree-view" id="tree-view" hidden></div>
                    </c:if>

                    <c:if test="${tool.tableOutput}">
                        <iframe class="preview-frame" id="preview-frame" title="HTML table preview"
                                sandbox="" hidden></iframe>
                    </c:if>

                    <div class="detail-table-wrap" id="detail-wrap"
                         <c:if test="${empty result or not result.hasDetails}">hidden</c:if>>
                        <table class="detail-table" id="detail-table">
                            <thead>
                            <tr>
                                <c:choose>
                                    <c:when test="${tool.diffOutput}">
                                        <th>Path</th><th>Change</th><th>Left</th><th>Right</th>
                                    </c:when>
                                    <c:otherwise>
                                        <th>Location</th><th>Problem</th>
                                    </c:otherwise>
                                </c:choose>
                            </tr>
                            </thead>
                            <tbody>
                            <c:forEach var="row" items="${result.details}">
                                <tr>
                                    <c:choose>
                                        <c:when test="${tool.diffOutput}">
                                            <td class="mono"><c:out value="${row.path}" /></td>
                                            <td><span class="tag tag-<c:out value='${row.typeClass}'/>"><c:out value="${row.type}" /></span></td>
                                            <td class="mono"><c:out value="${row.left}" /></td>
                                            <td class="mono"><c:out value="${row.right}" /></td>
                                        </c:when>
                                        <c:otherwise>
                                            <td class="mono"><c:out value="${row.location}" /></td>
                                            <td><c:out value="${row.message}" /></td>
                                        </c:otherwise>
                                    </c:choose>
                                </tr>
                            </c:forEach>
                            </tbody>
                        </table>
                    </div>

                    <ul class="stats" id="stats"
                        <c:if test="${empty result or not result.hasStats}">hidden</c:if>>
                        <c:forEach var="stat" items="${result.stats}">
                            <li><span class="stat-label"><c:out value="${stat.key}" /></span>
                                <span class="stat-value"><c:out value="${stat.value}" /></span></li>
                        </c:forEach>
                    </ul>
                </div>
            </section>
        </div>
    </form>

    <%-- Samples ride along in hidden textareas (whose contents the browser
         entity-decodes, unlike a script block) so "Sample" needs no round trip. --%>
    <textarea id="sample-data" hidden readonly><c:out value="${sample}" /></textarea>
    <c:if test="${tool.dualInput}">
        <textarea id="sample-data-2" hidden readonly><c:out value="${secondSample}" /></textarea>
    </c:if>

    <input type="file" id="file-input" hidden
           accept=".json,.txt,.xml,.yaml,.yml,.csv,.tsv,.js,.log,application/json,text/plain,text/xml,text/csv">
</article>

<%@ include file="layout/footer.jsp" %>
