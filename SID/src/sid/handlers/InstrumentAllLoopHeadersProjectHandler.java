package sid.handlers;

import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.open.commons.utils.DisplayUtils;

import sid.dynamic.instruments.Instrument;
import sid.dynamic.phases.Instrumentation;

public class InstrumentAllLoopHeadersProjectHandler extends AbstractHandler {
	public InstrumentAllLoopHeadersProjectHandler() {}

	public Object execute(ExecutionEvent event) throws ExecutionException {
		
		try {
			IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			ISelection selection = window.getSelectionService().getSelection("org.eclipse.jdt.ui.PackageExplorer");

			if(selection == null){
				DisplayUtils.showMessage("Please select a project.");
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
			
			HashMap<GraphElement,LinkedList<Instrument>> instruments = Instrumentation.instrumentAllLoopHeaders(project);
			
			project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			
			DisplayUtils.showMessage("All " + instruments.keySet().size() + " loop headers have been instrumented.");
		} catch (Exception e) {
			DisplayUtils.showError(e, "Error creating driver project.");
		}

		return null;
	}

}