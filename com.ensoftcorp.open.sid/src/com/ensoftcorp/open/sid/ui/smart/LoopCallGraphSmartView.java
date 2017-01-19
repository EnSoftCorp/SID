package com.ensoftcorp.open.sid.ui.smart;

import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.markup.MarkupFromH;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.query.Query;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.CommonQueries;
import com.ensoftcorp.atlas.core.script.FrontierStyledResult;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.scripts.selections.FilteringAtlasSmartViewScript;
import com.ensoftcorp.atlas.ui.scripts.selections.IResizableScript;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;
import com.ensoftcorp.open.sid.statics.LoopCallGraph;

/**
 * Input:
 * One or more Methods
 * 
 * 
 * Output:
 * For each input Method, the Loop Call Graph.
 * If a Method has a Loop or can reach a Method which has a loop, 
 * it will be in the call graph.  Otherwise the result is the empty graph. 
 * 
 * 
 * Highlights:
 * * Methods containing loops == bright BLUE
 * * Call edges which may have been from a CallSite within a loop == ORANGE
 *
 */
public class LoopCallGraphSmartView extends FilteringAtlasSmartViewScript implements IResizableScript{
	
	@Override
	protected String[] getSupportedNodeTags() {
		return new String[]{XCSG.Method};
	}

	@Override
	protected String[] getSupportedEdgeTags() {
		return NOTHING;
	}

	@Override
	public String getTitle() {
		return "Loop Call Graph (LCG)";
	}

	@Override
	public int getDefaultStepTop() {
		return 1;
	}

	@Override
	public int getDefaultStepBottom() {
		return 1;
	}

	@Override
	public FrontierStyledResult evaluate(IAtlasSelectionEvent event, int reverse, int forward) {
		
		Q filteredSelection = filter(event.getSelection());
		if (CommonQueries.isEmpty(filteredSelection)){
			return null;
		}

		LoopCallGraph lcg = new LoopCallGraph();
		Q lcgQ = lcg.lcg();
		
		Q callQ = Query.universe().edgesTaggedWithAny(XCSG.Call);
		
		lcgQ = lcgQ.union(lcgQ.reverseOn(callQ));
		lcgQ = Common.resolve(null, lcgQ);
		
		Highlighter h = lcg.colorMethodsCalledFromWithinLoops();

		// compute what to show for current steps
		Q completeResult = lcgQ;
		Q f = filteredSelection.forwardStepOn(completeResult, forward);
		Q r = filteredSelection.reverseStepOn(completeResult, reverse);
		Q result = f.union(r).union(filteredSelection);;
		
		// compute what is on the frontier
		Q frontierForward = filteredSelection.forwardStepOn(completeResult, forward+1);
		Q frontierReverse = filteredSelection.reverseStepOn(completeResult, reverse+1);
		frontierForward = frontierForward.retainEdges().differenceEdges(result);
		frontierReverse = frontierReverse.retainEdges().differenceEdges(result);

		return new FrontierStyledResult(result, frontierReverse, frontierForward, new MarkupFromH(h));
	}

	@Override
	protected StyledResult selectionChanged(IAtlasSelectionEvent input, Q filteredSelection) {
		return null;
	}

}
