<%@ taglib uri="a" prefix="fortify" %>

<fortify:greeting />

<%
String userInput = request.getParameter("username");
%>

<%=userInput%>