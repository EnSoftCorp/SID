package tca.analysis.statically;

import static com.ensoftcorp.atlas.core.script.Common.codemap;
import static com.ensoftcorp.atlas.core.script.Common.toGraph;
import static com.ensoftcorp.atlas.core.script.Common.toQ;
import static com.ensoftcorp.atlas.core.script.CommonQueries.callStep;
import static com.ensoftcorp.atlas.core.script.CommonQueries.declarations;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.NodeGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.CommonQueries.TraversalDirection;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

/**
 * Common queries which are useful for writing larger analysis programs, 
 * and for using on the shell.
 * 
 * @author Tom Deering, Ben Holland
 */
public final class StandardQueries {
	
	private StandardQueries() {}
	
	private static Q index() {
		return codemap();
	}

	/**
	 * Everything declared under the given methods, but NOT declared under
	 * additional methods or types. Retrieves declarations of only this method.
	 * 
	 * Operates in the index context.
	 * 
	 * @param origin
	 * @return
	 */
	public static Q localDeclarations(Q origin) {
		return localDeclarations(index(), origin);
	}

	/**
	 * Everything declared under the given methods, but NOT declared under
	 * additional methods or types. Retrieves declarations of only this method.
	 * 
	 * Operates in the given context.
	 * 
	 * @param context
	 * @param origin
	 * @return
	 */
	public static Q localDeclarations(Q context, Q origin) {
		Q dec = context.edgesTaggedWithAny(XCSG.Contains);
		dec = dec.differenceEdges(dec.reverseStep(dec.nodesTaggedWithAny(XCSG.Type)));
		return dec.forward(origin);
	}

	/**
	 * Returns the direct callers of the given methods.
	 * 
	 * Operates in the index context.
	 * 
	 * @param origin
	 * @return
	 */
	public static Q callers(Q origin) {
		return callers(index(), origin);
	}

	/**
	 * Returns the direct callers of the given methods.
	 * 
	 * Operates in the given context.
	 * 
	 * @param context
	 * @param origin
	 * @return
	 */
	public static Q callers(Q context, Q origin) {
		return callStep(context, origin, TraversalDirection.REVERSE).retainEdges().roots();
	}

	/**
	 * Returns the subset of the given methods which are called.
	 * 
	 * Operates in the index context.
	 * 
	 * @param origin
	 * @return
	 */
	public static Q called(Q origin) {
		return called(index(), origin);
	}

	/**
	 * Returns the subset of the given methods which are called.
	 * 
	 * Operates in the given context.
	 * 
	 * @param context
	 * @param origin
	 * @return
	 */
	public static Q called(Q context, Q origin) {
		return callStep(context, origin, TraversalDirection.REVERSE).retainEdges().leaves();
	}

	/**
	 * Returns the given methods which were called by the given callers.
	 * 
	 * Operates in the index context.
	 * 
	 * @param callers
	 * @param called
	 * @return
	 */
	public static Q calledBy(Q callers, Q called) {
		return calledBy(index(), callers, called);
	}

	/**
	 * Returns the given methods which were called by the given callers.
	 * 
	 * Operates in the given context.
	 * 
	 * @param context
	 * @param callers
	 * @param called
	 * @return
	 */
	public static Q calledBy(Q context, Q callers, Q called) {
		return context.edgesTaggedWithAny(XCSG.Call).betweenStep(callers, called).retainEdges().leaves();
	}

	/**
	 * Returns the first declaring node of the given Q which is tagged with one
	 * of the given types.
	 * 
	 * Operates in the index context.
	 * 
	 * @param declared
	 * @param declaratorTypes
	 * @return
	 */
	public static Q firstDeclarator(Q declared, String... declaratorTypes) {
		return firstDeclarator(index(), declared, declaratorTypes);
	}

	/**
	 * Returns the first declaring node of the given Q which is tagged with one
	 * of the given types.
	 * 
	 * Operates in the given context.
	 * 
	 * @param context
	 * @param declared
	 * @param declaratorTypes
	 * @return
	 */
	public static Q firstDeclarator(Q context, Q declared, String... declaratorTypes) {
		Q subContext = declarations(context, declared, TraversalDirection.REVERSE);
		subContext = subContext.differenceEdges(subContext.reverseStep(subContext.nodesTaggedWithAny(declaratorTypes)));
		return subContext.reverse(declared).nodesTaggedWithAny(declaratorTypes);
	}

	/**
	 * Given two query expressions, intersects the given node and edge kinds to
	 * produce a new expression.
	 * 
	 * @param first
	 * @param second
	 * @param nodeTags
	 * @param edgeTags
	 * @return
	 */
	public static Q advancedIntersection(Q first, Q second, String[] nodeTags, String[] edgeTags) {
		Q plainIntersection = first.intersection(second);

		return plainIntersection.nodesTaggedWithAny(nodeTags).induce(plainIntersection.edgesTaggedWithAny(edgeTags));
	}

	/**
	 * Returns the nodes which directly read from nodes in origin.
	 * 
	 * Operates in the index context.
	 * 
	 * @param origin
	 * @return
	 */
	public static Q readersOf(Q origin) {
		return readersOf(index(), origin);
	}

	/**
	 * Returns the nodes which directly read from nodes in origin.
	 * 
	 * Operates in the given context.
	 * 
	 * @param context
	 * @param origin
	 * @return
	 */
	public static Q readersOf(Q context, Q origin) {
		return context.edgesTaggedWithAny(XCSG.DataFlow_Edge).successors(origin);
	}

	/**
	 * Returns the nodes which directly write to nodes in origin.
	 * 
	 * Operates in the index context.
	 * 
	 * @param origin
	 * @return
	 */
	public static Q writersOf(Q origin) {
		return writersOf(index(), origin);
	}

	/**
	 * Returns the nodes which directly write to nodes in origin.
	 * 
	 * Operates in the given context.
	 * 
	 * @param context
	 * @param origin
	 * @return
	 */
	public static Q writersOf(Q context, Q origin) {
		return context.edgesTaggedWithAny(XCSG.DataFlow_Edge).predecessors(origin);
	}

	/**
	 * Returns the nodes from which nodes in the origin read.
	 * 
	 * Operates in the index context.
	 * 
	 * @param origin
	 * @return
	 */
	public static Q readBy(Q origin) {
		return readBy(index(), origin);
	}

	/**
	 * Returns the nodes from which nodes in the origin read.
	 * 
	 * Operates in the given context.
	 * 
	 * @param context
	 * @param origin
	 * @return
	 */
	public static Q readBy(Q context, Q origin) {
		return writersOf(context, origin);
	}

	/**
	 * Returns the nodes to which nodes in origin write.
	 * 
	 * Operates in the index context.
	 * 
	 * @param origin
	 * @return
	 */
	public static Q writtenBy(Q origin) {
		return writtenBy(index(), origin);
	}

	/**
	 * Returns the nodes to which nodes in origin write.
	 * 
	 * Operates in the given context.
	 * 
	 * @param context
	 * @param origin
	 * @return
	 */
	public static Q writtenBy(Q context, Q origin) {
		return readersOf(context, origin);
	}

	
	/**
	 * Returns the containing method of a given graph element or null if one is not found
	 * @param ge
	 * @return
	 */
	public static GraphElement getContainingMethod(GraphElement ge) {
		// NOTE: the enclosing method may be two steps or more above
		return getContainingNode(ge, XCSG.Method);
	}

	/**
	 * Returns the containing method of a given Q or empty if one is not found
	 * @param nodes
	 * @return
	 */
	public static Q getContainingMethods(Q nodes) {
		AtlasSet<GraphElement> nodeSet = nodes.eval().nodes();
		AtlasSet<GraphElement> containingMethods = new AtlasHashSet<GraphElement>();
		for (GraphElement currentNode : nodeSet) {
			GraphElement method = getContainingMethod(currentNode);
			if (method != null)
				containingMethods.add(method);
		}
		return toQ(toGraph(containingMethods));
	}
	
	public static GraphElement getContainingControlFlow(GraphElement ge) {
		// NOTE: the enclosing control flow node may be two steps or more above
		return getContainingNode(ge, XCSG.ControlFlow_Node);
	}

	/**
	 * Find the next immediate containing node with the given tag.
	 * 
	 * @param node 
	 * @param containingTag
	 * @return the next immediate containing node, or null if none exists; never returns the given node
	 */
	private static GraphElement getContainingNode(GraphElement node,
			String containingTag) {
		if (node == null)
			return null;
		
		while (true) {
			GraphElement containsEdge = Graph.U.edges(node, NodeDirection.IN).taggedWithAll(XCSG.Contains).getFirst();
			if (containsEdge == null)
				return null;
			
			GraphElement parent = containsEdge.getNode(EdgeDirection.FROM);
			
			if (parent.taggedWith(containingTag))
				return parent;
			
			node = parent;
		}
	
	}

	/**
	 * Returns the control flow graph between conditional nodes and the given
	 * origin.
	 * 
	 * Operates within the index context.
	 * 
	 * @param origin
	 * @return
	 */
	public static Q conditionsAbove(Q origin) {
		return conditionsAbove(index(), origin);
	}

	/**
	 * Returns the control flow graph between conditional nodes and the given
	 * origin.
	 * 
	 * Operates within the given context.
	 * 
	 * @param context
	 * @param origin
	 * @return
	 */
	public static Q conditionsAbove(Q context, Q origin) {
		Q conditionNodes = context.nodesTaggedWithAny(XCSG.ControlFlowCondition);

		return context.edgesTaggedWithAny(XCSG.ControlFlow_Edge).between(conditionNodes, origin);
	}

	/**
	 * Given a Q containing methods or data flow nodes, returns a Q of things
	 * which write to or call things in the Q.
	 * 
	 * Operates within the index context.
	 * 
	 * @param origin
	 * @return
	 */
	public static Q mutators(Q origin) {
		return mutators(index(), origin);
	}

	/**
	 * Returns those nodes in the context which have self edges.
	 * 
	 * @param context
	 * @return
	 */
	public static Q nodesWithSelfEdges(Q context) {
		AtlasSet<GraphElement> res = new AtlasHashSet<GraphElement>();

		for (GraphElement edge : context.eval().edges()) {
			GraphElement to = edge.getNode(EdgeDirection.TO);
			GraphElement from = edge.getNode(EdgeDirection.FROM);
			if (to == from)
				res.add(to);
		}

		return toQ(new NodeGraph(res));
	}

	/**
	 * Given a Q containing methods or data flow nodes, returns a Q of things
	 * which write to or call things in the Q.
	 * 
	 * Operates within the index context.
	 * 
	 * @param context
	 * @param origin
	 * @return
	 */
	public static Q mutators(Q context, Q origin) {
		return writersOf(context, origin).union(callers(context, origin));
	}

	/**
	 * Returns those elements in the origin which were called by or written by
	 * elements in the mutators set.
	 * 
	 * Operates within the index context.
	 * 
	 * @param mutators
	 * @param origin
	 * @return
	 */
	public static Q mutatedBy(Q mutators, Q origin) {
		return mutatedBy(index(), mutators, origin);
	}

	/**
	 * Returns those elements in the origin which were called by or written by
	 * elements in the mutators set.
	 * 
	 * Operates within the given context.
	 * 
	 * @param context
	 * @param mutators
	 * @param origin
	 * @return
	 */
	public static Q mutatedBy(Q context, Q mutators, Q origin) {
		return writtenBy(context, origin).union(calledBy(context, origin, mutators)).intersection(origin);
	}
}
