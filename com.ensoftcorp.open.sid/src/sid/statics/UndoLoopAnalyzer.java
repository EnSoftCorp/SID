package sid.statics;

import static com.ensoftcorp.atlas.core.query.Query.universe;

import java.util.Iterator;

import com.ensoftcorp.atlas.core.db.graph.Edge;
import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.xcsg.XCSG;

/**
 * Removes Tags and Attributes contributed by the LoopAnalyzer.
 * This is primarily intended for informal experiments and debugging.  
 * 
 * @author Jon Mathews
 */
public class UndoLoopAnalyzer {

	public static void undoLoopAnalyzer() {
		Log.info("Clearing tags and attributes contributed by LoopAnalyzer");
		
		Iterator<Node> itr = universe().nodesTaggedWithAny(XCSG.ControlFlow_Node).eval().nodes().iterator(); 
		while (itr.hasNext()){  
			GraphElement ge = itr.next(); 
			ge.untag(LoopAnalyzer.CFGNode.LOOP_HEADER); 
			ge.untag(LoopAnalyzer.CFGNode.NATURAL_LOOP); 
			ge.removeAttr(LoopAnalyzer.CFGNode.LOOP_HEADER_ID); 
			ge.removeAttr(LoopAnalyzer.CFGNode.LOOP_MEMBER_ID);   
		}
		
		Iterator<Edge> itrE = universe().edgesTaggedWithAny(XCSG.ControlFlow_Edge, XCSG.ExceptionalControlFlow_Edge).eval().edges().iterator();
		while (itrE.hasNext()) {  
			GraphElement ge = itrE.next(); 
			ge.untag(LoopAnalyzer.CFGEdge.LOOP_REENTRY_EDGE); 
			ge.untag(LoopAnalyzer.CFGEdge.LOOP_BACK_EDGE);   
		}
		
		Log.info("Finished clearing tags and attributes contributed by LoopAnalyzer");
	}
}
