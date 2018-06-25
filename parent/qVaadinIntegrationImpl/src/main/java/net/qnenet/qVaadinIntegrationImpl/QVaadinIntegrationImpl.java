package net.qnenet.qVaadinIntegrationImpl;

import java.util.Collections;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.Servlet;
import javax.servlet.annotation.WebServlet;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.osgi.service.log.LogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vaadin.osgi.resources.OsgiVaadinResources;
import com.vaadin.osgi.resources.VaadinResourceService;
import com.vaadin.osgi.resources.OsgiVaadinResources.ResourceBundleInactiveException;
import com.vaadin.server.Constants;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.UI;

import net.qnenet.qVaadinIntegration.OsgiUIProvider;
import net.qnenet.qVaadinIntegration.OsgiVaadinServlet;

@Component
public class QVaadinIntegrationImpl {

	static final Logger LOG = LoggerFactory.getLogger(QVaadinIntegrationImpl.class);

    private static final String MISSING_ANNOTATION_MESSAGE_FORMAT = "The property '%s' must be set in a '%s' without the '%s' annotation!";
    private static final String URL_PATTERNS_NOT_SET_MESSAGE_FORMAT = "The property '%s' must be set when either the 'urlPatterns' or 'value' attribute is not set in the annotation!";

    private static final String SERVLET_PATTERN = HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN;

    private static final String VAADIN_RESOURCES_PARAM = HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_INIT_PARAM_PREFIX
            + Constants.PARAMETER_VAADIN_RESOURCES;

    private OsgiUIProvider uiProvider = new OsgiUIProvider();
	
    private final Map<ServiceReference<VaadinServlet>, ServiceRegistration<Servlet>> registeredServlets = Collections
            .synchronizedMap(new LinkedHashMap<>());

	@Activate
	void activate() throws InterruptedException {
		LOG.info("Hello");
	}

	@Deactivate
	void deactivate(Map<String, Object> map) {
		LOG.info("Goodbye");
	}
	
    @SuppressWarnings("unused")
	@Reference(cardinality = ReferenceCardinality.MULTIPLE, service = VaadinServlet.class, policy = ReferencePolicy.DYNAMIC)
    void bindVaadinServlet(VaadinServlet servlet, ServiceReference<VaadinServlet> reference)
            throws ResourceBundleInactiveException {
    	
        Hashtable<String, Object> properties = getProperties(reference);

        WebServlet annotation = servlet.getClass().getAnnotation(WebServlet.class);
    	
        if (!validateSettings(annotation, properties)) {
            return;
        }
        properties.put(VAADIN_RESOURCES_PARAM, getResourcePath());
        if (annotation != null) {
            properties.put(HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_ASYNC_SUPPORTED,
                    Boolean.toString(annotation.asyncSupported()));
        }
        // We register the Http Whiteboard servlet using the context of
        // the bundle which registered the Vaadin Servlet, not our own
        BundleContext bundleContext = reference.getBundle().getBundleContext();
        if (servlet instanceof OsgiVaadinServlet) {
            ((OsgiVaadinServlet) servlet).setUIProvider(uiProvider);
        } else {
        	LOG.warn("The servlet is not an instance of OsgiVaadinServlet. If you are using Declarative Services in your UI your dependencies will not work");
        }
        // If the servlet pattern is not set in the properties but it's set in the
        // annotation use that. Some implementations of the HttpService seem to use the
        // annotation but it's not necessary and the priority is the specified way of
        // setting the pattern
        if (!properties.containsKey(SERVLET_PATTERN) && annotation != null) {
            String pattern = getPatternFromAnnotation(annotation);
            properties.put(SERVLET_PATTERN, pattern);
        }

        ServiceRegistration<Servlet> servletRegistration = bundleContext.registerService(Servlet.class, servlet,
                properties);

        registeredServlets.put(reference, servletRegistration);
   	
    	
    	
    }

    void unbindVaadinServlet(ServiceReference<VaadinServlet> reference) {
        ServiceRegistration<?> servletRegistration = registeredServlets.remove(reference);
        if (servletRegistration != null) {
            try {
                servletRegistration.unregister();
            } catch (IllegalStateException ise) {
                // This service may have already been unregistered
                // automatically by the OSGi framework if the
                // application bundle is being stopped. This is
                // obviously not a problem for us.
            }
        }
    }
    

    
    private Hashtable<String, Object> getProperties(ServiceReference<VaadinServlet> reference) {
        Hashtable<String, Object> properties = new Hashtable<>();
        for (String key : reference.getPropertyKeys()) {
            properties.put(key, reference.getProperty(key));
        }
        return properties;
    }
    
    private boolean validateSettings(WebServlet annotation, Hashtable<String, Object> properties) {
        if (!properties.containsKey(SERVLET_PATTERN)) {
            if (annotation == null) {
//                log(LogService.LOG_ERROR, String.format(MISSING_ANNOTATION_MESSAGE_FORMAT, SERVLET_PATTERN,
//                        VaadinServlet.class.getSimpleName(), WebServlet.class.getName()));
            	
                LOG.error(String.format(MISSING_ANNOTATION_MESSAGE_FORMAT, SERVLET_PATTERN,
                        VaadinServlet.class.getSimpleName(), WebServlet.class.getName()));

                return false;
            } else if (annotation.urlPatterns().length == 0 && annotation.value().length == 0) {
            	LOG.error(String.format(URL_PATTERNS_NOT_SET_MESSAGE_FORMAT, SERVLET_PATTERN));
                return false;
            }
        }
        return true;
    }


    private String getResourcePath() throws ResourceBundleInactiveException {
        VaadinResourceService service = OsgiVaadinResources.getService();
        return String.format("/%s", service.getResourcePathPrefix());
    }
    
    private String getPatternFromAnnotation(WebServlet annotation) {
        String[] patterns = annotation.urlPatterns();
        String[] value = annotation.value();

        if (patterns.length > 0 && value.length > 0) {
        	LOG.error("The servlet specification doesn't allow both urlPatterns and value to be specified in the WebServlet annotation");
            throw new IllegalStateException();
        }

        String[] argument;
        if (patterns.length > 0) {
            argument = patterns;
        } else if (value.length > 0) {
            argument = value;
        } else {
            throw new IllegalStateException(
                    "When specifying the url pattern through the WebServlet annotation you must specify either value or urlPatterns");
        }

        if (argument.length > 1) {
            String arguments = Stream.of(argument).skip(1).collect(Collectors.joining(","));
            LOG.warn("Using the first pattern as the urlPattern, the following will be ignored : " + arguments);
        }
        return argument[0];
    }

   
    @Reference(cardinality = ReferenceCardinality.MULTIPLE, service = UI.class, policy = ReferencePolicy.DYNAMIC)
    void bindUI(UI ui, ServiceReference<UI> reference) {
        BundleContext context = reference.getBundle().getBundleContext();
        uiProvider.bindUI(ui, context.getServiceObjects(reference));
    }

    void unbindUI(UI ui) {
        uiProvider.unbindUI(ui);
    }


}
