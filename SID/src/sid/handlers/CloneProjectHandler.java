package sid.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.ensoftcorp.open.commons.utils.DisplayUtils;

import sid.dynamic.phases.Cloning;
import sid.dynamic.phases.Instrumentation;
import sid.dynamic.phases.Setup;

public class CloneProjectHandler extends AbstractHandler {
	public CloneProjectHandler() {
	}

	public Object execute(ExecutionEvent event) throws ExecutionException {
		IProject cloneProject = null;
		try {
			// get the package explorer selection
			IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			ISelection selection = window.getSelectionService().getSelection("org.eclipse.jdt.ui.PackageExplorer");
			
			if(selection == null){
				DisplayUtils.showMessage("Please select a project to clone.");
				return null;
			}
			
			TreePath[] paths = ((TreeSelection) selection).getPaths();
			TreePath p = paths[0];
			Object last = p.getLastSegment();
			
			// locate the project handle for the selection
			IProject project = null;
			if(last instanceof IJavaProject){
				project = ((IJavaProject) last).getProject();
			} else if (last instanceof IResource) {
				project = ((IResource) last).getProject();
			} 
			
			// clone the project and add the jimple instruments
			
			IProject dynamicSupportProject = Setup.getOrCreateDynamicSupportProject();
			if(!dynamicSupportProject.exists()){
				DisplayUtils.showMessage("Unable to locate DynamicSupport project.");
				return null;
			}
			if(project != null){
				cloneProject = Cloning.clone(project, "clone");
				Instrumentation.addInstruments(cloneProject, dynamicSupportProject);
			} else {
				DisplayUtils.showMessage("Invalid selection. Please select the project to clone.");
			}
		} catch (Exception e) {
			DisplayUtils.showError(e, "Error cloning project.");
			if(cloneProject != null){
				Setup.deleteProject(cloneProject);
			}
		}
		
		return null;
	}

}