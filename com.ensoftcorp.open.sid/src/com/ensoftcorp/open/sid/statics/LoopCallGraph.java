package com.ensoftcorp.open.sid.statics;

import static com.ensoftcorp.atlas.core.script.Common.resolve;
import static com.ensoftcorp.atlas.core.script.Common.toQ;
import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.graph.operation.InducedGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.highlight.Highlighter.ConflictStrategy;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.algorithms.StronglyConnectedComponents;
import com.ensoftcorp.open.commons.analysis.CommonQueries;
import com.ensoftcorp.open.jimple.commons.loops.DecompiledLoopIdentification.CFGNode;
import com.ensoftcorp.open.sid.log.Log;

public class LoopCallGraph {
	
	/**
	 * An integer attribute added to a CFG Node to indicate the intra-procedural loop nesting depth
	 */
	public final static String NESTING_DEPTH = "NESTING_DEPTH";
	
	/**
	 * A call graph, consisting of XCSG.Method nodes and XCSG.Call edges
	 */
	private Q callContext;
	
	/**
	 * The call graph from call sites to methods
	 */
	private Q callsitesCallEdges() { return universe().edgesTaggedWithAny(XCSG.InvokedFunction, XCSG.InvokedSignature); };
	
	public LoopCallGraph() {
		this.callContext = resolve(null, universe().edgesTaggedWithAny(XCSG.Call));
	}
	
	/**
	 * @param callContext A call graph, consisting of XCSG.Method nodes and XCSG.Call edges
	 */
	public LoopCallGraph(Q callContext) {
		this.callContext = resolve(null, callContext);
	}
	
	/**
	 * Constructs the loop call graph (lcg) of a given system, An lcg is a subset of the system's call graph
	 * where the roots are the root methods containing loops and the leaves are the leaf methods containing loops 
	 * @return lcg
	 */
	public Q lcg(){
		Q loopingMethods = getMethodsContainingLoops();
		Q callEdges = getCallGraph();
		Q callGraph = callEdges.between(loopingMethods, loopingMethods);
		Q recursiveMethods = LoopCallGraph.getRecursiveMethods();
		return callGraph.union(recursiveMethods);
	}
	
	/**
	 * Returns a Highlighter coloring the methods containing loops and edges corresponding to call from within loops
	 * 
	 * Methods == bright BLUE
	 * Call Edges which may have been from within a loop == ORANGE
	 * 
	 * @return
	 */
	public Highlighter colorMethodsCalledFromWithinLoops(){
		AtlasSet<GraphElement> callEdgesFromWithinLoops = new AtlasHashSet<GraphElement>();
		// Get all the call sites
		Q allCallsites = universe().nodesTaggedWithAll(XCSG.CallSite);
		
		// Retrieve the CFG nodes that are containing the call sites and are part of a loop
		Q loopingCFGNodes = getContainingLoopingCFGNodes(allCallsites);
		
		//Iterate through the looping CFG nodes and compute the call graph for the methods called from within loops
		for(GraphElement cfgNode : loopingCFGNodes.eval().nodes()){
			// Get the methods containing the current cfgNode
			Q caller = CommonQueries.getContainingFunctions(toQ(cfgNode));
			
			// Get the call site contained within the cfgNode
			AtlasSet<Node> loopingCallsites = toQ(cfgNode).children().nodesTaggedWithAll(XCSG.CallSite).eval().nodes();
			if (loopingCallsites.size() != 1)
				Log.warning("Internal error, expected exactly one callsite");
			GraphElement loopingCallsite = loopingCallsites.getFirst();
			
			// Get the possible target methods from this call site
			Q targetMethods = CallSite.getTargetMethods(loopingCallsite);

			// The resulting call graph should be between the called and the invoked methods
			Q subResult = getCallGraph().betweenStep(caller, targetMethods);

			callEdgesFromWithinLoops.addAll(subResult.eval().edges());
		}
		
		Q recursiveMethods = LoopCallGraph.getRecursiveMethods().retainNodes();
		Q methodsContainingLoops = getMethodsContainingLoops();
		
		Q recursiveLoopMethods = recursiveMethods.intersection(methodsContainingLoops);
		
		Q recursiveOnlyMethods = recursiveMethods.difference(recursiveLoopMethods);
		Q loopOnlyMethods = methodsContainingLoops.difference(recursiveLoopMethods);
		
		Highlighter h = new Highlighter(ConflictStrategy.LAST_MATCH);
		
		// Highlight methods containing loops with BLUE
		h.highlight(loopOnlyMethods, Color.BLUE.brighter().brighter());
		
		// Highlight methods participating in recursion MAGENTA
		h.highlight(recursiveOnlyMethods, Color.MAGENTA.brighter().brighter());
		
		// Highlight methods participating in both loops and recursion RED
		h.highlight(recursiveLoopMethods, Color.RED.brighter().brighter());

		// Retrieve the call edges from the resultant graph and color them ORANGE
		h.highlightEdges(toQ(callEdgesFromWithinLoops), Color.ORANGE);
		
		return h;
	}
	
	/**
	 * Calculate the depth of intra- and inter-procedural loop nesting 
	 */
	public void calculateLoopNestingHeight(){
		// Before start traversing the call graph, tag the call sites called from within loop with its intra-procedural loop nesting depth
		Q loopingCFGNodes = TagCallSitesIntraprocedualNestingLoopingDepth();
		Q results = Common.empty();
		
		///Iterate through the looping CFG nodes and compute the call graph for the methods called from within loops 
		for(GraphElement cfgNode : loopingCFGNodes.eval().nodes()){
			// Get the methods containing the current cfgNode
			Q caller = CommonQueries.getContainingFunctions(toQ(cfgNode));
			
			// Get the call sites containing within the cfgNode
			Q loopingCallsites = toQ(cfgNode).children().nodesTaggedWithAll(XCSG.CallSite);
			
			// Get the invoked methods from these call sites
			Q invokedMethods = callsitesCallEdges().successors(loopingCallsites).nodesTaggedWithAll(XCSG.Method);
			
			// The resulting call graph should be between the called and the invoked methods
			Q subResult = getCallGraph().betweenStep(caller, invokedMethods);
			//results = results.union((caller.union(invokedMethods)).induce(getCallGraph()));
			results = results.union(subResult);
		}
		
		// Retrieve the call edges from the resultant graph and color them with RED
		AtlasSet<Edge> callEdgesFromWithinLoops = results.eval().edges();
		
		Q lcg = lcg();
		AtlasSet<Node> methodsContainingLoops = getMethodsContainingLoops().eval().nodes();

		// Iterate through the methods containing loops and compute its loop nesting depth
		for(GraphElement method : methodsContainingLoops){
			// The LCG for the current method
			Graph graph = lcg.forward(toQ(method)).eval();
			
			// Recursively traverse the LCG for the method and compute its loop nesting depth
			int depth = traverse(graph, method, 1, new AtlasHashSet<Node>(), callEdgesFromWithinLoops);
			Log.info(method.getAttr(XCSG.name) + "\t" + depth);
		}
	}
	
	/**
	 * Performs DFS traversal on the given call graph from the current node to calculate the depth of loop nesting
	 * @param graph: The graph to traverse
	 * @param node: The current node traversed
	 * @param depth: The current depth
	 * @param visited: The set of node visited along the traversal
	 * @param callEdgesFromWithinLoops: The edges corresponding to call from within a loop
	 * @return the current depth at the node visited
	 */
	private int traverse(Graph graph, GraphElement node, int depth, AtlasSet<Node> visited, AtlasSet<Edge> callEdgesFromWithinLoops){
		// If the node has been visited before, return the current depth
		if(visited.contains(node)){
			return depth;
		}
		visited.add(node);
		AtlasSet<GraphElement> outEdges = graph.edges(node, NodeDirection.OUT);
		ArrayList<Integer> depths = new ArrayList<Integer>();
		// Recursively iterate through the children of the current node
		for(GraphElement edge : outEdges){
			GraphElement child = edge.getNode(EdgeDirection.TO);
			int depth_new = depth;

			// If the child is being called from within a loop then add the nesting depth to the current depth
			if(callEdgesFromWithinLoops.contains(edge)){
				// Get the call sites for the child in the caller (node)
				Q callsites = getCallSitesForMethodInCaller(toQ(child), toQ(node));
				
				// Retrieves the set of CFG nodes containing the call sites and are part of loop
				Q loopingCFGNodes = getContainingCFGNodes(callsites).selectNode(NESTING_DEPTH);
				ArrayList<Integer> iDepths = new ArrayList<Integer>();
				if(loopingCFGNodes.eval().nodes().isEmpty()){
//					DisplayUtils.show(Common.toQ(edge), null, true, "no");
				}
				
				// Iterate through the looping CFG nodes and compute the maximum nesting depth
				for(GraphElement cfgCallsite : loopingCFGNodes.eval().nodes()){
					int intraprocedural_depth = (int)cfgCallsite.getAttr(NESTING_DEPTH);
					iDepths.add(intraprocedural_depth);
				}
				depth_new += Collections.max(iDepths);
				depth_new++; // From inter-procedural nesting
			}
			int d = traverse(graph, child, depth_new, new AtlasHashSet<Node>(visited), callEdgesFromWithinLoops);
			depths.add(d);
		}
		// Calculate the maximum depth and return it
		if(!depths.isEmpty()){
			depth = Collections.max(depths);
		}
		return depth;
	}
	
	/**
	 * Returns the set of call sites for the callee in the caller
	 * @param callee
	 * @param caller
	 * @return
	 */
	private Q getCallSitesForMethodInCaller(Q callee, Q caller){
		Q callsites = getCallSitesForMethods(callee);
		Q containedCallsites = caller.contained().nodesTaggedWithAll(XCSG.CallSite).intersection(callsites);
		return containedCallsites;
	}
	
	/**
	 * Returns the set of call sites for the passed method(s)
	 * @param methods
	 * @return
	 */
	private Q getCallSitesForMethods(Q methods){
		Q callsites = callsitesCallEdges().predecessors(methods).nodesTaggedWithAll(XCSG.CallSite);
		return callsites;
	}
	
	/**
	 * Returns the set of CFG nodes tagged with LOOP_HEADER that are containing within the passed method
	 * @param method
	 * @return
	 */
	private Q getLoopHeadersForMethod(Q method){
		Q loopHeaders = method.contained().nodesTaggedWithAll(XCSG.ControlFlow_Node, CFGNode.LOOP_HEADER);
		return loopHeaders;
	}
	
	/**
	 * Returns the set of control flow nodes that are part of a loop and containing the passed nodes
	 * @param nodes
	 * @return
	 */
	private Q getContainingLoopingCFGNodes(Q nodes){
		Q containingCFGNodes = getContainingCFGNodes(nodes);
		Q loopingCFGNodes = containingCFGNodes.selectNode(CFGNode.LOOP_MEMBER_ID);
		loopingCFGNodes = loopingCFGNodes.union(containingCFGNodes.nodesTaggedWithAll(CFGNode.LOOP_HEADER));
		return loopingCFGNodes;
	}
	
	/**
	 * Returns the set of control flow nodes that are containing the passed nodes
	 * @param callsites
	 * @return
	 */
	private Q getContainingCFGNodes(Q callsites) {
		Q CFGNodes = callsites.parent().nodesTaggedWithAll(XCSG.ControlFlow_Node);
		return CFGNodes;
	}
	
	/**
	 * Adds the attribute NESTING_DEPTH to all call sites that are part of a loop
	 * @returns the set of CFG node containing the calls from within loops
	 */
	private Q TagCallSitesIntraprocedualNestingLoopingDepth(){
		// Get all call sites
		Q allCallsites = universe().nodesTaggedWithAll(XCSG.CallSite);
		
		// Get only the containing CFG nodes that are part of a loop 
		Q loopingCFGNodes = getContainingLoopingCFGNodes(allCallsites);
		AtlasSet<Node> nodes = loopingCFGNodes.eval().nodes();
		
		// Iterate through the looping CFG nodes and add the NESTING_DEPTH attribute
		for(GraphElement node : nodes){
			Q loopHeaders = getLoopHeadersForMethod(CommonQueries.getContainingFunctions(toQ(node)));
			int depth = calculateIntraproceduralLoopNestingDepth(node, 0, loopHeaders);
			node.putAttr(NESTING_DEPTH, depth);
		}
		return loopingCFGNodes;
	}
	
	/**
	 * Returns the set of loop header for the loops that are nested within other loops
	 * @return the set of nested loop headers
	 */
	public Q getNestedLoopHeaders(){
		Q loopHeaders = universe().nodesTaggedWithAll(CFGNode.LOOP_HEADER);
		Q nestedLoopHeaders = Common.empty();
		for(GraphElement header : loopHeaders.eval().nodes()){
			Q method = CommonQueries.getContainingFunctions(toQ(header));
			Q headersInMethods = getLoopHeadersForMethod(method);
			int depth = calculateIntraproceduralLoopNestingDepth(header, 1, headersInMethods);
			if(depth > 0){
				// A (depth > 0) means that the loop is nested
				nestedLoopHeaders = nestedLoopHeaders.union(toQ(header));
			}
		}
		return nestedLoopHeaders;
	}
	
	/**
	 * For a given CFG Node, recursively computes the number of loops this node is nested under
	 * @param ge the CFG node within a loop
	 * @param depth the current depth
	 * @param loopHeaders the set of loop headers in the containing method
	 * @return the nesting depth
	 */
	public int calculateIntraproceduralLoopNestingDepth(GraphElement ge, int depth, Q loopHeaders){
		if(ge.hasAttr(CFGNode.LOOP_MEMBER_ID)){
			int loopHeaderId = (int)ge.getAttr(CFGNode.LOOP_MEMBER_ID);
			GraphElement loopHeader = loopHeaders.selectNode(CFGNode.LOOP_HEADER_ID, loopHeaderId).eval().nodes().getFirst();
			depth = ge.taggedWith(CFGNode.LOOP_HEADER) ? ++depth : depth;
			return calculateIntraproceduralLoopNestingDepth(loopHeader, depth, loopHeaders);
		}
		return depth;
	}
	
	/**
	 * Retrieves the XCSG.Call graph
	 * @return the call graph
	 */
	public Q getCallGraph(){
		return callContext;
	}
	
	/**
	 * Returns the set of methods that are containing loops based on the DLI algorithm
	 * @return The set of methods containing loops
	 */
	public static Q getMethodsContainingLoops(){
		Q headers = Common.universe().nodesTaggedWithAny(CFGNode.LOOP_HEADER);
		Q loopingMethods = CommonQueries.getContainingFunctions(headers);
		return loopingMethods;
	}
	
	public int callsitesinlcg(){
		Q lcg = lcg();
		Q methods = lcg.nodesTaggedWithAll(XCSG.Method);
		int count = 0;
		for(GraphElement method : methods.eval().nodes()){
			AtlasSet<Node> callsites = toQ(method).contained().nodesTaggedWithAll(XCSG.CallSite).eval().nodes();
			for(GraphElement callsite : callsites){
				Q invokedMethod = callsitesCallEdges().successors(toQ(callsite));
				if(!methods.intersection(invokedMethod).eval().nodes().isEmpty()){
					count++;
				}
			}
		}
		return count;
	}
	
	public void check(){
		Q lcg = lcg();
		AtlasSet<Edge> edges = lcg.edgesTaggedWithAll(XCSG.Call).eval().edges();
		for(GraphElement edge : edges){
			GraphElement from = edge.getNode(EdgeDirection.FROM);
			GraphElement to = edge.getNode(EdgeDirection.TO);
			Q callsites = getCallSitesForMethodInCaller(toQ(to), toQ(from));
			if(callsites.eval().nodes().isEmpty()){
//				DisplayUtils.show(Common.toQ(from), null, true, "1");
//				DisplayUtils.show(Common.toQ(to), null, true, "2");
			}
		}
	}
	
	public Q getCallEdgesFromWithinLoops(){
		
		Q results = Common.empty();
		// Get all the call sites
		Q allCallsites = universe().nodesTaggedWithAll(XCSG.CallSite);
		
		// Retrieve the CFG nodes that are containing the call sites and are part of a loop
		Q loopingCFGNodes = getContainingLoopingCFGNodes(allCallsites);
		
		//Iterate through the looping CFG nodes and compute the call graph for the methods called from within loops
		for(GraphElement cfgNode : loopingCFGNodes.eval().nodes()){
			// Get the methods containing the current cfgNode
			Q caller = CommonQueries.getContainingFunctions(toQ(cfgNode));
			
			// Get the call sites containing within the cfgNode
			Q loopingCallsites = toQ(cfgNode).children().nodesTaggedWithAll(XCSG.CallSite);
			
			// Get the invoked methods from these call sites
			Q invokedMethods = callsitesCallEdges().successors(loopingCallsites).nodesTaggedWithAll(XCSG.Method);
			
			// The resulting call graph should be between the called and the invoked methods
			Q subResult = getCallGraph().betweenStep(caller, invokedMethods);

			results = results.union(subResult);
		}
		// Retrieve the call edges from the resultant graph and color them with RED
		return results.edgesTaggedWithAll(XCSG.Call);
	}

	/**
	 * Runs the loop analysis
	 * @return
	 */
	public static LoopCallGraph getLoopCallGraph(){
		LoopCallGraph lcg = new LoopCallGraph();		
		return lcg;
	}
	
	// jgrapht library version
	public static Q getRecursiveMethods(){
		Q callEdges = Common.universe().edgesTaggedWithAny(XCSG.Call).retainEdges();
		StronglyConnectedComponents adapter = new StronglyConnectedComponents(callEdges.eval().nodes(), callEdges.eval().edges());
		AtlasSet<Node> recursionNodes = new AtlasHashSet<Node>();
		AtlasSet<Edge> recursionEdges = new AtlasHashSet<Edge>();
		for(AtlasSet<Node> scc : adapter.findSCCs()){
			Q recursion = Common.toQ(scc).induce(callEdges);
			Graph recursionGraph = recursion.eval();
			if(recursionGraph.edges().size() > 0){
				// SCC must have at least one edge to be recursive
				recursionNodes.addAll(recursionGraph.nodes());
				recursionEdges.addAll(recursionGraph.edges());
			}
		}
		Q recursiveMethods = Common.toQ(recursionNodes).induce(Common.toQ(recursionEdges));
		return recursiveMethods;
	}
	
	// native atlas version
//	public static Q getRecursiveMethods(){
//		Q callEdges = Common.universe().edgesTaggedWithAny(XCSG.Call);
//		Q[] sccs = stronglyConnectedComponents(callEdges);
//		
////		JGraphTAdapter jgraphtAdapter = new JGraphTAdapter(methods.eval().nodes(), callEdges.eval().edges());
//		AtlasSet<Node> recursionNodes = new AtlasHashSet<Node>();
//		AtlasSet<Edge> recursionEdges = new AtlasHashSet<Edge>();
//		for(Q scc : sccs){
//			Q recursion = scc.retainEdges();
//			Graph recursionGraph = recursion.eval();
//			if(recursionGraph.edges().size() > 0){
//				recursionNodes.addAll(recursionGraph.nodes());
//				recursionEdges.addAll(recursionGraph.edges());
//			}
//		}
//		Q recursiveMethods = Common.toQ(recursionNodes).induce(Common.toQ(recursionEdges));
//		return recursiveMethods;
//	}

	/**
	 * Returns a resolved and highlighted loop call graph for a given entry point method
	 * @param method
	 * @return
	 */
	public static StyledResult getMethodLCG(GraphElement method, LoopCallGraph lcg){
		return getMethodSubLevelLCG(method, lcg, 0);
	}
	
	/**
	 * Returns a specified level within the loop call graph for a given entry point method
	 * @param method
	 * @param lcg
	 * @param level
	 * @return
	 */
	public static StyledResult getMethodSubLevelLCG(GraphElement method, LoopCallGraph lcg, int level){
		Q callGraph = Common.universe().edgesTaggedWithAny(XCSG.Call);
		Q children = Common.toQ(method);
		for(int i=0; i<level; i++){
//			children = lcg.getCallEdgesFromWithinLoops().successors(children); // step along looping edges (yellow edges)
			children = callGraph.successors(children); // step along the call graph
		}
		Q methodsCallGraph = callGraph.forward(children);
		Q methodSubLevelLCG = methodsCallGraph.intersection(lcg.lcg());
		Highlighter h = lcg.colorMethodsCalledFromWithinLoops();
		methodSubLevelLCG = methodSubLevelLCG.union(callGraph.forwardStep(Common.toQ(method))); //TODO: fix nasty hack...for bad lcg level
		StyledResult result = new StyledResult(Common.resolve(null, methodSubLevelLCG), h);
		return result;
	}
	
	/**
	 * Given a context, returns all strongly connected components in
	 * that context. The resulting array contains each separate SCC found.
	 * 
	 * @param context
	 * @return
	 */
	public static Q[] stronglyConnectedComponents(Q context){
		Graph contextG = context.eval();
		
		Graph[] sccs = new TarjanSCC().scc(contextG, contextG.nodes());
		
		Q[] res = new Q[sccs.length];
		for(int i=0; i<sccs.length; i++) res[i] = toQ(sccs[i]);
	
		return res;
	}
	
	/**
	 * Implementation of Tarjan's algorithm for finding strongly connected components.
	 * 
	 * https://en.wikipedia.org/wiki/Tarjan%27s_strongly_connected_components_algorithm
	 * 
	 * @author Tom Deering
	 *
	 */
	private static class TarjanSCC {
		private int verticesReached = 0;
		private Map<GraphElement, Integer> preorderMap;
		private Map<GraphElement, Integer> lowlinkMap;
		private Map<Integer, AtlasSet<GraphElement>> sccMap;
		private int cycleNum = 0;
		private Stack<GraphElement> s;
		private Graph context;
		
		/**
		 * 
		 * http://en.wikipedia.org/wiki/Tarjan%
		 * 27s_strongly_connected_components_algorithm
		 * 
		 * @param context
		 * @param atlasSet
		 * @return
		 */
		public Graph[] scc(Graph context, AtlasSet<Node> atlasSet) {
			verticesReached = 0;
			preorderMap = new HashMap<GraphElement, Integer>((int) atlasSet.size() * 2);
			lowlinkMap = new HashMap<GraphElement, Integer>((int) atlasSet.size() * 2);
			sccMap = new HashMap<Integer, AtlasSet<GraphElement>>();
			s = new Stack<GraphElement>();
			this.context = context;

			for (GraphElement fromNode : atlasSet){
				if (!preorderMap.containsKey(fromNode)){
					sccWalk(fromNode);
				}
			}

			Graph[] res = new Graph[sccMap.size()];
			int i = 0;
			for (AtlasSet<GraphElement> nodeSet : sccMap.values()) {
				res[i++] = new InducedGraph(nodeSet, context.edges());
			}

			return res;
		}

		private void sccWalk(GraphElement fromNode) {
			preorderMap.put(fromNode, verticesReached);
			lowlinkMap.put(fromNode, verticesReached);
			verticesReached += 1;
			s.push(fromNode);

			for (GraphElement outgoingEdge : context.edges(fromNode, NodeDirection.OUT)) {
				GraphElement dest = outgoingEdge.getNode(EdgeDirection.TO);

				if (!preorderMap.containsKey(dest)) {
					sccWalk(dest);
					lowlinkMap.put(fromNode, minLowLink(fromNode, dest));
				} else if (s.contains(dest)) {
					lowlinkMap.put(fromNode, minLowLink(fromNode, dest));
				}
			}

			if (preorderMap.get(fromNode).equals(lowlinkMap.get(fromNode))) {
				GraphElement popped;
				AtlasSet<GraphElement> allPopped = new AtlasHashSet<GraphElement>();
				do {
					popped = s.pop();
					allPopped.add(popped);
				} while (popped != fromNode);
				if (allPopped.size() > 1) {
					sccMap.put(cycleNum++, allPopped);
				}
			}
		}

		private Integer minLowLink(GraphElement first, GraphElement second) {
			Integer ours = lowlinkMap.get(first);
			Integer theirs = lowlinkMap.get(second);
			return ours.compareTo(theirs) < 0 ? ours : theirs;
		}
	}
}
