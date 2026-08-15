<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="layout/header.jsp" %>

<section class="hero">
    <h1>JSON Formatter, Validator &amp; Converter</h1>
    <p class="hero-lead">
        <c:out value="${toolCount}" /> tools for working with JSON, XML, YAML and CSV &mdash;
        beautify and validate documents, convert between formats, compare two files,
        and generate model classes in five languages.
    </p>
    <div class="hero-actions">
        <a class="button button-primary" href="${ctx}/json-formatter">Format JSON</a>
        <a class="button" href="${ctx}/json-validator">Validate JSON</a>
        <a class="button" href="${ctx}/json-viewer">View as tree</a>
    </div>
</section>

<c:forEach var="group" items="${categories}">
    <c:if test="${not empty group.value}">
        <section class="tool-section">
            <h2 class="section-heading"><c:out value="${group.key.label}" /></h2>
            <div class="card-grid">
                <c:forEach var="item" items="${group.value}">
                    <a class="tool-card" href="${ctx}${item.url}">
                        <h3><c:out value="${item.title}" /></h3>
                        <p><c:out value="${item.tagline}" /></p>
                    </a>
                </c:forEach>
            </div>
        </section>
    </c:if>
</c:forEach>

<%@ include file="layout/footer.jsp" %>
