package tca.analysis.statically;

import static com.ensoftcorp.atlas.core.script.Common.resolve;
import static com.ensoftcorp.atlas.core.script.Common.universe;

import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.ontopia.utils.CompactHashMap;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ensoftcorp.atlas.core.db.graph.Graph;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.EdgeDirection;
import com.ensoftcorp.atlas.core.db.graph.GraphElement.NodeDirection;
import com.ensoftcorp.atlas.core.db.graph.operation.ForwardGraph;
import com.ensoftcorp.atlas.core.db.set.AtlasHashSet;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.db.set.SingletonAtlasSet;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

/**
 * Uses algorithm from Wei et al. to identify loops, even irreducible ones.
 * 
 * "A New Algorithm for Identifying Loops in Decompilation".
 * Static Analysis Lecture Notes in Computer Science Volume 4634, 2007, pp 170-183
 * http://link.springer.com/chapter/10.1007%2F978-3-540-74061-2_11
 * http://www.lenx.100871.net/papers/loop-SAS.pdf
 * 
 * @author tdeering
 *
 */
public class LoopAnalyzer implements Runnable{
	public static interface CFGNode{
		/**
		 * Tag applied to loop header CFG node
		 */
		public static final String LOOP_HEADER = "LOOP_HEADER";
		
		/**
		 * Tag applied to loop reentry CFG node
		 */
		public static final String LOOP_REENTRY_NODE = "LOOP_REENTRY_NODE";
		
		/**
		 * Tag applied to irreducible loop headers
		 */
		public static final String IRREDUCIBLE_LOOP = "IRREDUCIBLE_LOOP";
		
		/**
		 * Tag applied to natural loop headers (a LOOP_HEADER not tagged IRREDUCIBLE_LOOP).
		 */
		public static final String NATURAL_LOOP = "NATURAL_LOOP";
		
		/**
		 * Integer attribute identifier, matches the LOOP_HEADER_ID for the innermost loop header of this node.
		 */
		public static final String LOOP_MEMBER_ID = "LOOP_MEMBER_ID";
		
		/**
		 * Integer attribute identifier for this loop header.
		 */
		public static final String LOOP_HEADER_ID = "LOOP_HEADER_ID";
	}

	public static interface CFGEdge{
		/**
		 * Tag for ControlFlow_Edge indicating a loop re-entry.
		 * Also called a "cross edge".
		 */
		public static final String LOOP_REENTRY_EDGE = "LOOP_REENTRY_EDGE";
		
		/**
		 * Tag for loop back edges
		 */
		public static final String LOOP_BACK_EDGE = "LOOP_BACK_EDGE";
	}
	
	public static void analyzeLoops() {
		analyzeLoops(new NullProgressMonitor());
	}
	
	/**
	 * Identify all loop fragments, headers, re-entries, and nesting in the universe 
	 * graph, applying the tags and attributes in interfaces CFGNode and CFGEdge.
	 * 
	 * NOTE: Handles both natural and irreducible loops
	 * 
	 * @return
	 */
	public static void analyzeLoops(IProgressMonitor monitor) {
		
		try {
		
			// Find the work to be done
			Q u = universe();
			Graph cfContextG = resolve(null, u.edgesTaggedWithAny(XCSG.ControlFlow_Edge, XCSG.ExceptionalControlFlow_Edge).eval());
			AtlasSet<GraphElement> cfRoots = u.nodesTaggedWithAny(XCSG.controlFlowRoot).eval().nodes();
			int work = (int) cfRoots.size();
			ArrayList<GraphElement> rootList = new ArrayList<GraphElement>(work);
			for(GraphElement root : cfRoots) rootList.add(root);
			
			monitor.beginTask("Identify Local Loops", rootList.size());
			
			// Assign the work to worker threads
			int procs = Runtime.getRuntime().availableProcessors();
			Thread[] threads = new Thread[procs];
			int workPerProc = work / procs;
			int remainder = work % procs;
			for(int i = 0; i < procs; ++i){
				int firstInclusive = workPerProc * i + Math.min(remainder, i);
				int lastExclusive = firstInclusive + workPerProc + (i < remainder ? 1:0);
				threads[i] = new Thread(new LoopAnalyzer(monitor, cfContextG, rootList.subList(firstInclusive, lastExclusive)));
				threads[i].start();
			}
			
			// Wait for worker threads to finish
			int waitIndex = 0;
			while(waitIndex < threads.length){
				if(!State.TERMINATED.equals(threads[waitIndex].getState())){
					try {
						threads[waitIndex].join();
					} catch (InterruptedException e) {}
				}else{
					++waitIndex;
				}
			}
		
		} finally {
			monitor.done();
		}
	}
	
	private AtlasSet<GraphElement> traversed, reentryNodes, reentryEdges, irreducible, loopbacks;
	private Graph cfContextG;
	
	/** The node's position in the DFSP (Depth-first search path) */
	private Map<GraphElement, Integer> dfsp;
	private Map<GraphElement, GraphElement> innermostLoopHeaders;
	private List<GraphElement> cfRoots;
	private static int idGenerator;
	private static Object idGeneratorLock = new Object();
	private IProgressMonitor monitor;
	
	private LoopAnalyzer(IProgressMonitor monitor, Graph cfContextG, List<GraphElement> cfRoots){
		this.monitor = monitor;
		this.cfContextG = cfContextG;
		this.cfRoots = cfRoots;
		traversed = new AtlasHashSet<GraphElement>();
		reentryNodes = new AtlasHashSet<GraphElement>();
		reentryEdges = new AtlasHashSet<GraphElement>();
		irreducible = new AtlasHashSet<GraphElement>();
		loopbacks = new AtlasHashSet<GraphElement>();
		dfsp = new CompactHashMap<GraphElement, Integer>();
		innermostLoopHeaders = new CompactHashMap<GraphElement, GraphElement>();
	}
	
	@Override
	public void run(){
		// Compute individually on a per-function basis
		for(GraphElement root : cfRoots){
			try{
				// Clear data from previous function
				reentryNodes.clear();
				reentryEdges.clear();
				irreducible.clear();
				traversed.clear();
				innermostLoopHeaders.clear();
				loopbacks.clear();
				dfsp.clear();

				for(GraphElement ge : new ForwardGraph(cfContextG, new SingletonAtlasSet<GraphElement>(root)).nodes())
					dfsp.put(ge, 0);
				
				// Loop identification algorithm
				loopDFS(root, 1);
				
				// Modify universe graph 
				Collection<GraphElement> loopHeaders = innermostLoopHeaders.values();
				AtlasSet<GraphElement> loopHeadersSet = new AtlasHashSet<GraphElement>();
				loopHeadersSet.addAll(loopHeaders);

				Map<GraphElement, Integer> loopHeaderToID = new CompactHashMap<GraphElement, Integer>((int)loopHeadersSet.size());
				
				synchronized(idGeneratorLock){
					for(GraphElement header : loopHeadersSet){
						int id = idGenerator++;
						loopHeaderToID.put(header, id);
						header.tag(CFGNode.LOOP_HEADER);
						header.putAttr(CFGNode.LOOP_HEADER_ID, id);
						if(irreducible.contains(header)){
							header.tag(CFGNode.IRREDUCIBLE_LOOP);
						}else{
							header.tag(CFGNode.NATURAL_LOOP);
						}
					}
				}

				for(GraphElement cfgNode : innermostLoopHeaders.keySet()){
					cfgNode.putAttr(CFGNode.LOOP_MEMBER_ID, loopHeaderToID.get(innermostLoopHeaders.get(cfgNode)));
				}
				
				for(GraphElement reentryNode : reentryNodes)
					reentryNode.tag(CFGNode.LOOP_REENTRY_NODE);
				
				for(GraphElement reentryEdge : reentryEdges)
					reentryEdge.tag(CFGEdge.LOOP_REENTRY_EDGE);
				
				for(GraphElement loopbackEdge : loopbacks)
					loopbackEdge.tag(CFGEdge.LOOP_BACK_EDGE);
			}catch(Throwable t){
				Log.error("Problem in loop analyzer thread for CFG root:\n" + root, t);
			}
			
			if (monitor.isCanceled())
				return;
			synchronized (monitor) {
				monitor.worked(1);
			}
		}
	}
	
	/**
	 * Recursively traverse the current node, returning its innermost loop header
	 * @param b0
	 * @param position
	 * @return
	 */
	private void loopDFS(GraphElement b0, int position){
		traversed.add(b0);
		dfsp.put(b0, position);
		
		for(GraphElement cfgEdge : cfContextG.edges(b0, NodeDirection.OUT)){
			GraphElement b = cfgEdge.getNode(EdgeDirection.TO);
			
			if(!traversed.contains(b)){
				// Paper Case A
				//  new
				loopDFS(b, position + 1);
				GraphElement nh = innermostLoopHeaders.get(b);
				tag_lhead(b0, nh);
			}
			else{
				if(dfsp.get(b) > 0){
					// Paper Case B
					//  Mark b as a loop header
					loopbacks.add(cfgEdge);
					tag_lhead(b0, b);
				}else{
					GraphElement h = innermostLoopHeaders.get(b);
					if(h == null) {
						// Paper Case C
						//  do nothing
						continue;
					}
					
					if(dfsp.get(h) > 0){
						// Paper Case D
						//  h in DFSP(b0)
						tag_lhead(b0, h);
					}
					else{
						// Paper Case E
						//  h not in DFSP(b0)
						reentryNodes.add(b);
						reentryEdges.add(cfgEdge);
						irreducible.add(h);
						
						while((h = innermostLoopHeaders.get(h)) != null){
							if(dfsp.get(h) > 0){
								tag_lhead(b0, h);
								break;
							}
							irreducible.add(h);
						}
					}	
				}
			}
		}
		
		dfsp.put(b0, 0);
	}
	
	private void tag_lhead(GraphElement b, GraphElement h){
		if(h == null || h.equals(b)) return;
		GraphElement cur1 = b;
		GraphElement cur2 = h;
		
		GraphElement ih;
		while((ih = innermostLoopHeaders.get(cur1)) != null){
			if(ih.equals(cur2)) return;
			if(dfsp.get(ih) < dfsp.get(cur2)){
				innermostLoopHeaders.put(cur1, cur2);
				cur1 = cur2;
				cur2 = ih;
			}else{
				cur1 = ih;
			}
		}
		innermostLoopHeaders.put(cur1, cur2);
	}
}
