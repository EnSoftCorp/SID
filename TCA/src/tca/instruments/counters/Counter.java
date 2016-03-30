package tca.instruments.counters;

import org.eclipse.core.resources.IProject;

import tca.instruments.Instrument;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;

public abstract class Counter extends Instrument {
	
	public Counter(IProject project, GraphElement graphElement) {
		super(project, graphElement);
	}
	
}
