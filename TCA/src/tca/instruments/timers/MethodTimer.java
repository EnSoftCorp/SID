package tca.instruments.timers;

import java.util.LinkedList;

import org.eclipse.core.resources.IProject;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.query.Attr.Node;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

public class MethodTimer extends Timer {

	public MethodTimer(IProject project, GraphElement graphElement) {
		super(project, graphElement);
	}

	@Override
	public String[] getSupportedGraphElements() {
		return new String[]{ XCSG.Method };
	}

	@Override
	public boolean performInstrumentation(){
		GraphElement method = graphElement;
		SourceCorrespondence cfRoot = getMethodRoot(method);
		boolean success = false;
		// need to insert after the jimple @this and any @parameter assignments, 
		// which is always first after the type declarations
		success |= beginProbe(method.getAttr(XCSG.name).toString(), cfRoot, false);
		LinkedList<SourceCorrespondence> returns = getReturns(method);
		if(returns.isEmpty()){
			// void method, just find the CF leaves
			for(SourceCorrespondence cfLeaf : getMethodCFLeaves(method)){
				success |= endProbe(method.getAttr(XCSG.name).toString(), cfLeaf, true);
			}
		} else {
			for(SourceCorrespondence returnSC : returns){
				success |= endProbe(method.getAttr(XCSG.name).toString(), returnSC, true);
			}
		}
		return success;
	}

	@Override
	protected boolean beginProbe(String name, SourceCorrespondence sc, boolean beforeSourceCorrespondence) {
		try {
			if(beforeSourceCorrespondence){
				insert(sc.sourceFile, sc.offset, "\nstaticinvoke <ruler.instrumentation.RULER_Timer: void start(java.lang.String)>(\"" + sc.sourceFile.getLocation().toFile().getName() + "_" + name + "\");\n");
			} else {
				insert(sc.sourceFile, sc.offset+sc.length, "\nstaticinvoke <ruler.instrumentation.RULER_Timer: void start(java.lang.String)>(\"" + sc.sourceFile.getLocation().toFile().getName() + "_" + name + "\");\n");
			}
			return true;
		} catch (Exception e){
			return false;
		}
	}

	@Override
	protected boolean endProbe(String name, SourceCorrespondence sc, boolean beforeSourceCorrespondence) {
		try {
			if(beforeSourceCorrespondence){
				insert(sc.sourceFile, sc.offset, "\nstaticinvoke <ruler.instrumentation.RULER_Timer: void stop(java.lang.String)>(\"" + sc.sourceFile.getLocation().toFile().getName() + "_" + name + "\");\n");	
			} else {
				insert(sc.sourceFile, sc.offset + sc.length, "\nstaticinvoke <ruler.instrumentation.RULER_Timer: void stop(java.lang.String)>(\"" + sc.sourceFile.getLocation().toFile().getName() + "_" + name + "\");\n");
			}
			return true;
		} catch (Exception e){
			return false;
		}
	}
	
	// helper method to return the location of the method returns
	// TODO: how do we deal with void methods?  the result will be an empty list...
	private LinkedList<SourceCorrespondence> getReturns(GraphElement method){
		LinkedList<SourceCorrespondence> returnSourceCorrespondents = new LinkedList<SourceCorrespondence>();
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q masterReturn = containsEdges.forwardStep(Common.toQ(method)).nodesTaggedWithAny(XCSG.Return);
		Q dataFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.DataFlow_Edge);
		for(GraphElement returnNode : dataFlowEdges.predecessors(masterReturn).eval().nodes()){
			returnSourceCorrespondents.add((SourceCorrespondence) returnNode.getAttr(Node.SC));
		}
		return returnSourceCorrespondents;
	}
	
	// helper method to return the location of the root of the method control flow graph
	// this must be after any type declarations, this assignment, or parameter assignments
	private SourceCorrespondence getMethodRoot(GraphElement method){
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q declaredMethodCFNodes = containsEdges.forwardStep(Common.toQ(method)).nodesTaggedWithAny(XCSG.ControlFlow_Node);
		
		Q thisAndParameterNodes = Common.universe().nodesTaggedWithAny(XCSG.Identity, XCSG.Parameter);
		Q thisAndParameterCFNodes = containsEdges.predecessors(thisAndParameterNodes);
		Q methodThisAndParameterCFNodes = declaredMethodCFNodes.intersection(thisAndParameterCFNodes);
		
		SourceCorrespondence methodRoot = null;
		for(GraphElement node : methodThisAndParameterCFNodes.eval().nodes()){
			SourceCorrespondence sc = ((SourceCorrespondence) node.getAttr(Node.SC));
			if(sc.offset > methodRoot.offset){
				methodRoot = sc;
			}
		}
		
		// if there weren't any this or parameter assignments just default back to the root of the control flow for the method
		if(methodRoot == null){
			methodRoot = getMethodCFRoot(method);
		}
		
		return methodRoot;
	}
	
	// helper method to return the root of the control flow for a method
	private SourceCorrespondence getMethodCFRoot(GraphElement method){
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q controlFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.ControlFlow_Edge);
		Q declaredMethodCFNodes = containsEdges.forwardStep(Common.toQ(method)).nodesTaggedWithAny(XCSG.ControlFlow_Node);
		GraphElement firstCFNode = controlFlowEdges.forwardStep(declaredMethodCFNodes).roots().eval().nodes().getFirst();
		return (SourceCorrespondence) firstCFNode.getAttr(Node.SC);
	}
	
	private LinkedList<SourceCorrespondence> getMethodCFLeaves(GraphElement method){
		LinkedList<SourceCorrespondence> result = new LinkedList<SourceCorrespondence>();
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q controlFlowEdges = Common.universe().edgesTaggedWithAny(XCSG.ControlFlow_Edge);
		Q declaredMethodCFNodes = containsEdges.forwardStep(Common.toQ(method)).nodesTaggedWithAny(XCSG.ControlFlow_Node);
		for(GraphElement leaf : controlFlowEdges.forwardStep(declaredMethodCFNodes).leaves().eval().nodes()){
			result.add((SourceCorrespondence) leaf.getAttr(Node.SC));
		}
		return result;
	}

}
