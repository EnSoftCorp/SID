package sid.dynamic.phases;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

public class Cloning {
	
	/**
	 * Returns the clone of a given workspace project
	 * @param projectName
	 * @return
	 * @throws CoreException
	 */
	public static IProject clone(IProject project, String clonePrefix) throws CoreException {
		IProgressMonitor m = new NullProgressMonitor();
		IProjectDescription projectDescription = project.getDescription();
		String cloneName = getUniqueProjectName(project.getName(), clonePrefix);
		// create clone project in workspace
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IProjectDescription cloneDescription = workspaceRoot.getWorkspace().newProjectDescription(cloneName);
		// copy the project properties
		cloneDescription.setNatureIds(projectDescription.getNatureIds());
		cloneDescription.setReferencedProjects(projectDescription.getReferencedProjects());
		cloneDescription.setDynamicReferences(projectDescription.getDynamicReferences());
		cloneDescription.setBuildSpec(projectDescription.getBuildSpec());
		cloneDescription.setReferencedProjects(projectDescription.getReferencedProjects());
		
		// copy project files
		project.copy(cloneDescription, true, m);
		IProject clone = workspaceRoot.getProject(cloneName);
		
		// refresh everything
		clone.refreshLocal(IResource.DEPTH_INFINITE, m);
		return clone;
	}
	
	/**
	 * Incrementally searches for an available project name in the workspace for the clone project
	 * @param projectName
	 * @return
	 */
	public static String getUniqueProjectName(String projectName, String namePrefix){
		int version = 1;
		String result = projectName + "_" + namePrefix + version;
		while(ResourcesPlugin.getWorkspace().getRoot().getProject(result).exists()){
			version++;
			result = projectName + "_" + namePrefix + version;
		}
		return result;
	}
	
}
