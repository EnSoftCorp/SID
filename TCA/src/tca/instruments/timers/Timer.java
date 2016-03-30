package tca.instruments.timers;

import org.eclipse.core.resources.IProject;

import tca.instruments.Instrument;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;

public abstract class Timer extends Instrument {
	
	public Timer(IProject project, GraphElement graphElement) {
		super(project, graphElement);
	}

	protected abstract boolean beginProbe(String name, SourceCorrespondence sc, boolean beforeSourceCorrespondence);
	
	protected abstract boolean endProbe(String name, SourceCorrespondence sc, boolean beforeSourceCorrespondence);

}
