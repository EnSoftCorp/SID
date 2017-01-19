package com.ensoftcorp.open.sid.dynamic.instruments.counters;

import java.io.IOException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.index.common.SourceCorrespondence;
import com.ensoftcorp.atlas.core.query.Attr.Node;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.jimple.commons.loops.DecompiledLoopIdentification.CFGNode;

public class LoopIterationCounter extends Counter {
	
	private String measurementKeyName;
	
	/**
	 * Inserts an counter just before the loop header
	 * Logs each iteration along with the system timestamp
	 * @param project
	 * @param graphElement
	 * @param logDirectory
	 */
	public LoopIterationCounter(IProject project, GraphElement loopHeader) {
		super(project, loopHeader);
		measurementKeyName = getQualifiedLabelName(loopHeader);
	}

	@Override
	public String[] getSupportedGraphElements() {
		return new String[]{CFGNode.LOOP_HEADER};
	}

	@Override
	public boolean performInstrumentation() {
		GraphElement loopHeader = graphElement;
		SourceCorrespondence sc = (SourceCorrespondence) loopHeader.getAttr(Node.SC);
		try {
			// insert just before the loop header
			insert(sc.sourceFile, sc.offset, "\nstaticinvoke <tca.instrumentation.TCA_Counter: void probe(java.lang.String)>(\"" + measurementKeyName + "\");\n");
		} catch (IOException | CoreException e) {
			return false;
		}
		return true;
	}
	
	// helper method to get a qualified name of the loop header
	// should produce a string like "MyProject.mypackage.MyClass.myMethod.label1"
	private String getQualifiedLabelName(GraphElement loopHeader){
		String name = loopHeader.getAttr(XCSG.name).toString();
		name = name.substring(0, name.indexOf(":"));
		// qualify the label
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		GraphElement parent = containsEdges.predecessors(Common.toQ(loopHeader)).eval().nodes().getFirst();
		while(parent != null){
			// skip adding qualified part for default package
			if(!(parent.tags().contains(XCSG.Package) && parent.getAttr(XCSG.name).toString().equals(""))){
				name = parent.getAttr(XCSG.name).toString() + "." + name;
			}
			parent = containsEdges.predecessors(Common.toQ(parent)).eval().nodes().getFirst();
		}
		return name;
	}

	@Override
	public String getMeasurementKeyName() {
		return measurementKeyName;
	}

}
