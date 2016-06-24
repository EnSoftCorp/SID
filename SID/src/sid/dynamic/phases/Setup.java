package sid.dynamic.phases;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.Bundle;

import sid.Activator;
import sid.log.Log;
import sid.setup.ProjectImporter;

public class Setup {

	public static IProject getOrCreateDynamicSupportProject() throws InvocationTargetException, InterruptedException, CoreException, IOException{
		IProject dynamicSupportProject = ResourcesPlugin.getWorkspace().getRoot().getProject("DynamicSupport");
		if(!dynamicSupportProject.exists()){
			Bundle dynamicBundle = Activator.getDefault().getBundle();
			dynamicSupportProject = ProjectImporter.importProject(new NullProgressMonitor(), dynamicBundle, "/support/" + "DynamicSupport.zip", "DynamicSupport");
		}
		return dynamicSupportProject;
	}
	
	public static IStatus deleteProject(IProject project) {
		if (project != null && project.exists())
			try {
				project.delete(true, true, new NullProgressMonitor());
			} catch (CoreException e) {
				Log.error("Could not delete project", e);
				return new Status(Status.ERROR, Activator.PLUGIN_ID, "Could not delete project", e);
			}
		return Status.OK_STATUS;
	}
	
}
