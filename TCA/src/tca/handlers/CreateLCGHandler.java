package tca.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import tca.analysis.statically.DisplayUtils;
import tca.analysis.statically.LoopCallGraph;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.selection.SelectionUtil;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class CreateLCGHandler extends AbstractHandler {
	public CreateLCGHandler() {}

	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		LoopCallGraph lcg = LoopCallGraph.getLoopCallGraph();

		IAtlasSelectionEvent selection = SelectionUtil.getLastSelectionEvent();
		Q selectionQ = selection.getSelection();
		AtlasSet<GraphElement> methodNodes = selectionQ.nodesTaggedWithAny(XCSG.Method).eval().nodes();

		if(methodNodes.isEmpty()){
			String message = "No methods selected to create LCG.";
			Log.warning(message);
			IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
			MessageDialog.openInformation(
					window.getShell(),
					"TCA",
					message);
			return null;
		}
		
		GraphElement method = methodNodes.getFirst();
		Log.info("Creating LCG for " + getQualifiedName(method));
		
		StyledResult methodLCG = LoopCallGraph.getMethodLCG(method, lcg);
		String title = getQualifiedName(method) + " LCG";
		DisplayUtils.show(methodLCG.getResult(), methodLCG.getHighlighter(), true, title);
		
		return null;
	}
	
	// helper method to get a qualified name of a method
	public static String getQualifiedName(GraphElement node){
		String name = node.getAttr(XCSG.name).toString();
		// qualify the label
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		GraphElement parent = containsEdges.predecessors(Common.toQ(node)).eval().nodes().getFirst();
		while(parent != null && !parent.tags().contains(XCSG.Project) && !parent.tags().contains(XCSG.Library)){
			// skip adding qualified part for default package
			if(!(parent.tags().contains(XCSG.Package) && parent.getAttr(XCSG.name).toString().equals(""))){
				name = parent.getAttr(XCSG.name).toString() + "." + name;
			}
			parent = containsEdges.predecessors(Common.toQ(parent)).eval().nodes().getFirst();
		}
		return name;
	}
}
