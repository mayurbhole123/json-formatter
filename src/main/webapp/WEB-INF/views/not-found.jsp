<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="layout/header.jsp" %>

<section class="hero">
    <h1>No such tool</h1>
    <p class="hero-lead">
        There is no tool at <code>/<c:out value="${missingPath}" /></code>.
        Pick one from the menu, or start with the formatter.
    </p>
    <div class="hero-actions">
        <a class="button button-primary" href="${ctx}/">All tools</a>
        <a class="button" href="${ctx}/json-formatter">JSON Formatter</a>
    </div>
</section>

<%@ include file="layout/footer.jsp" %>
