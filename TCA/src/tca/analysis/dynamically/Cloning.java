package tca.analysis.dynamically;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

public class Cloning {

	/**
	 * Returns the clone of a given workspace project
	 * @param projectName The name of the workspace project to clone
	 * @return
	 * @throws CoreException
	 */
	public static IProject clone(String projectName) throws CoreException {
		IProgressMonitor m = new NullProgressMonitor();
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = workspaceRoot.getProject(projectName);
		IProjectDescription projectDescription = project.getDescription();
		String cloneName = nameCloneProject(projectName + "_instrumented");
		// create clone project in workspace
		IProjectDescription cloneDescription = workspaceRoot.getWorkspace().newProjectDescription(cloneName);
		// copy project files
		project.copy(cloneDescription, true, m);
		IProject clone = workspaceRoot.getProject(cloneName);
		// copy the project properties
		cloneDescription.setNatureIds(projectDescription.getNatureIds());
		cloneDescription.setReferencedProjects(projectDescription.getReferencedProjects());
		cloneDescription.setDynamicReferences(projectDescription.getDynamicReferences());
		cloneDescription.setBuildSpec(projectDescription.getBuildSpec());
		cloneDescription.setReferencedProjects(projectDescription.getReferencedProjects());
		clone.setDescription(cloneDescription, null);
		return clone;
	}
	
	/**
	 * Incrementally searches for an available project name in the workspace for the clone project
	 * @param projectName
	 * @return
	 */
	private static String nameCloneProject(String projectName){
		int version = 2;
		String result = projectName + "_" + version;
		while(ResourcesPlugin.getWorkspace().getRoot().getProject(result).exists()){
			version++;
			result = projectName + "_" + version;
		}
		return result;
	}
	
}
