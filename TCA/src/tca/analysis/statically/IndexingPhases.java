package tca.analysis.statically;

import static com.ensoftcorp.atlas.core.script.Common.universe;
import tca.analysis.statically.LoopAnalyzer.CFGNode;

import com.ensoftcorp.atlas.core.log.Log;

public class IndexingPhases {

	public static void confirmLoopAnalyzer() {
		
		if(universe().nodesTaggedWithAny(CFGNode.LOOP_HEADER).eval().nodes().isEmpty()){
			Log.warning("Request for Loop analysis results before analyzer", new IllegalStateException());
		}

	}

}
