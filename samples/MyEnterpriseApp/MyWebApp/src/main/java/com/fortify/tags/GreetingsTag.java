package com.fortify.tags;

import javax.servlet.jsp.tagext.SimpleTagSupport;
import javax.servlet.jsp.JspException;
import java.io.IOException;

public class GreetingsTag extends SimpleTagSupport {
	public void doTag() throws JspException, IOException {
		getJspContext().getOut().print("Hello.");
	}
}

