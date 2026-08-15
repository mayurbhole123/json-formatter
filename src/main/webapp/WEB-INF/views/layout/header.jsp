<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" />
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${pageTitle}" /> &middot; JSON Tools</title>
    <meta name="description" content="Format, validate, convert and generate code from JSON, XML, YAML and CSV.">
    <link rel="icon" href="${ctx}/static/img/favicon.svg" type="image/svg+xml">
    <link rel="stylesheet" href="${ctx}/static/css/app.css">
</head>
<body data-ctx="${ctx}">
<a class="skip-link" href="#main">Skip to content</a>

<header class="topbar">
    <a class="brand" href="${ctx}/">
        <span class="brand-mark">{ }</span>
        <span class="brand-name">JSON Tools</span>
    </a>
    <div class="topbar-search">
        <label class="sr-only" for="tool-search">Search tools</label>
        <input type="search" id="tool-search" placeholder="Search tools&hellip;" autocomplete="off">
    </div>
    <button type="button" class="icon-button" id="nav-toggle" aria-label="Toggle tool menu" aria-expanded="false">
        <span></span><span></span><span></span>
    </button>
</header>

<div class="shell">
    <nav class="sidebar" id="sidebar" aria-label="Tools">
        <c:forEach var="group" items="${categories}">
            <c:if test="${not empty group.value}">
                <div class="nav-group" data-group>
                    <h2 class="nav-heading"><c:out value="${group.key.label}" /></h2>
                    <ul class="nav-list">
                        <c:forEach var="item" items="${group.value}">
                            <li>
                                <a href="${ctx}${item.url}"
                                   data-name="<c:out value="${item.title} ${item.tagline}" />"
                                   class="${not empty tool and tool.id eq item.id ? 'is-active' : ''}">
                                    <c:out value="${item.title}" />
                                </a>
                            </li>
                        </c:forEach>
                    </ul>
                </div>
            </c:if>
        </c:forEach>
    </nav>

    <main class="content" id="main">
