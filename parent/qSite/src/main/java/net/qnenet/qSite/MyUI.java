package net.qnenet.qSite;

import javax.servlet.annotation.WebServlet;

import org.osgi.service.component.annotations.Component;

import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.Button;
import com.vaadin.ui.Label;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

import net.qnenet.qVaadinIntegration.OsgiVaadinServlet;

/**
 * This UI is the application entry point. A UI may either represent a browser window 
 * (or tab) or some part of an HTML page where a Vaadin application is embedded.
 * <p>
 * The UI is initialized using {@link #init(VaadinRequest)}. This method is intended to be 
 * overridden to add component to the user interface and initialize non-component functionality.
 */
//@Component(service=UI.class)
//@Theme("qneTheme16")
@SuppressWarnings("serial")
public class MyUI extends UI {

    @Override
    protected void init(VaadinRequest vaadinRequest) {
    	
        final VerticalLayout layout = new VerticalLayout();
        
        final TextField name = new TextField();
        name.setCaption("Type your name here:");

        Button button = new Button("Click Me");
        button.addClickListener(e -> {
            layout.addComponent(new Label("Thanks " + name.getValue() 
                    + ", it works!"));
        });
        
        layout.addComponents(name, button);
        
        setContent(layout);
    }

@Component(service=VaadinServlet.class) //, scope=ServiceScope.PROTOTYPE) //, property =(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/test" ))
@WebServlet(urlPatterns = "/*", name = "MyUIServlet", asyncSupported = true)
    @VaadinServletConfiguration(ui = MyUI.class, productionMode = false)
    public static class MyUIServlet extends OsgiVaadinServlet {
    }
}
