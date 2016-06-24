package sid.statics;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.utils.StandardQueries;

import sid.log.Log;

public class CallSite {

	/**
	 * Given a call site, return formal identity arguments for possible target
	 * methods.
	 **/
	public static Q getIdentity(GraphElement callsite) {
		return getIdentity(Common.toQ(callsite));
	}

	public static Q getIdentity(Q callsites) {
		Q actualArgument = Common.universe().edgesTaggedWithAny(XCSG.IdentityPassedTo).predecessors(callsites);
		return Common.universe().edgesTaggedWithAny(XCSG.InterproceduralDataFlow).successors(actualArgument);
	}

	/**
	 * Given a callsite, return the method representing the invoked signature
	 * 
	 * @param callsite
	 * @return
	 */
	public static Q getSignature(GraphElement callsite) {
		return getSignature(Common.toQ(callsite));
	}

	public static Q getSignature(Q callsites) {
		Q signature = Common.universe().edgesTaggedWithAny(XCSG.InvokedFunction, XCSG.InvokedSignature).successors(callsites);
		return signature;
	}

	/**
	 * Given a StaticDispatchCallSite or a DynamicDispatchCallSite, return the
	 * methods which may have been invoked.
	 * 
	 * @param callsite
	 * @return
	 */
	public static Q getTargetMethods(GraphElement callsite) {
		// Note: nodes and edges currently need not be bounded (i.e. any ModelElement is acceptable)
		// The following are used if present:
		// nodes <- DataFlow_Node | Variable
		// edges <- DataFlow_Edge | InvokedSignature | InvokedFunction | IdentityPassedTo
		Graph dataFlowGraph = Common.universe().eval();
		return getTargetMethods(new NullProgressMonitor(), dataFlowGraph, callsite);
	}

	private static Q getTargetMethods(IProgressMonitor monitor, Graph dataFlowGraph, GraphElement callsite) {
		if (callsite.taggedWith(XCSG.StaticDispatchCallSite)) {
			return Common.toQ(dataFlowGraph).edgesTaggedWithAny(XCSG.InvokedFunction).successors(Common.toQ(Common.toGraph(callsite)));
		} else if (callsite.taggedWith(XCSG.DynamicDispatchCallSite)) {
			AtlasSet<GraphElement> targetMethods = new AtlasHashSet<GraphElement>();
			AtlasSet<Node> targetIdentities = getIdentity(dataFlowGraph, callsite).eval().nodes();
			for (GraphElement targetIdentity : targetIdentities) {
				GraphElement targetMethod = StandardQueries.getContainingMethod(targetIdentity);
				if (targetMethod != null){
					targetMethods.add(targetMethod);
				} else {
					Log.warning("Cannot find containing Method for Identity: " + targetIdentity);
				}
				if (monitor.isCanceled()){
					throw new OperationCanceledException();
				}
			}
			return Common.toQ(Common.toGraph(targetMethods));
		}
		throw new IllegalArgumentException();
	}

	/**
	 * Given a call site, return formal identity arguments for possible target
	 * methods.
	 **/
	private static Q getIdentity(Graph dataFlowGraph, GraphElement callsite) {
		return getIdentity(Common.toQ(dataFlowGraph), Common.toQ(callsite));
	}

	private static Q getIdentity(Q dataFlowGraph, Q callsites) {
		Q actualArgument = dataFlowGraph.edgesTaggedWithAny(XCSG.IdentityPassedTo).predecessors(callsites);
		return dataFlowGraph.edgesTaggedWithAny(XCSG.InterproceduralDataFlow).successors(actualArgument);
	}

}