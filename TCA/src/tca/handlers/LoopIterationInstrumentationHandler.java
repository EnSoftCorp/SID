package tca.handlers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import tca.Activator;
import tca.analysis.dynamically.Cloning;
import tca.analysis.dynamically.Compilation;
import tca.analysis.dynamically.Instrumentation;
import tca.analysis.statically.LoopCallGraph;
import tca.instruments.Instrument;
import tca.instruments.counters.LoopIterationCounter;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.log.Log;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.selection.SelectionUtil;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;

/**
 * Our sample handler extends AbstractHandler, an IHandler base class.
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class LoopIterationInstrumentationHandler extends AbstractHandler {
	public LoopIterationInstrumentationHandler() {}

	/**
	 * the command has been executed, so extract extract the needed information
	 * from the application context.
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		
		IAtlasSelectionEvent selection = SelectionUtil.getLastSelectionEvent();
		Q selectionQ = selection.getSelection();
		AtlasSet<GraphElement> methodNodes = selectionQ.nodesTaggedWithAny(XCSG.Method).eval().nodes();
		
		if(methodNodes.isEmpty()){
			MessageDialog.openInformation(
					window.getShell(),
					"TCA",
					"No methods selected for instrumentation!");
		} else if(methodNodes.size() > 1){
			MessageDialog.openInformation(
					window.getShell(),
					"TCA",
					"Please only select one method at a time for loop iteration counters!"
					+ "Method children are automatically instrumented.\n");
		}
		
		GraphElement method = methodNodes.getFirst();
		
		// get the method loop call graph for selected methods
		LoopCallGraph lcg = LoopCallGraph.getLoopCallGraph();
		StyledResult methodLCG = LoopCallGraph.getMethodLCG(method, lcg);
		
		// get the project name
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		GraphElement parent = methodNodes.getFirst();
		while(parent != null && !parent.tags().contains(XCSG.Project)){
			parent = containsEdges.predecessors(Common.toQ(parent)).eval().nodes().getFirst();
		}
		String projectName = parent.getAttr(XCSG.name).toString();
		
		// instrument the binary
		try {
			Log.info("Instrumenting...");
			IProject clone = Cloning.clone(projectName);
			String experimentName = clone.getName();
			IProject jimpleInstruments = ResourcesPlugin.getWorkspace().getRoot().getProject("JimpleInstruments");

			LinkedList<Instrument> instruments = new LinkedList<Instrument>();
			
			// instrument the bytecode and compile
			Q loopHeaders = containsEdges.forward(methodLCG.getResult()).nodesTaggedWithAny("LOOP_HEADER");
			for(GraphElement loopHeader : loopHeaders.eval().nodes()){
				instruments.add(new LoopIterationCounter(clone, loopHeader));
			}
			
			// will need to reset edit engine if the Atlas index is updated!
//			// EditEngine.getInstance().reset();
			IFolder jimpleDirectory = Instrumentation.instrument(clone, jimpleInstruments, instruments);
			File instrumentedBytecode = File.createTempFile(experimentName, ".jar");
			Compilation.compile(clone, jimpleDirectory, instrumentedBytecode);
			
			// create a driver for the instrumented bytecode
			Log.info("Creating skeleton driver...");
			createDriver(method, projectName + "_driver", instrumentedBytecode, jimpleInstruments, true);
			
			Log.info("Deleting temporary instrumentation files...");
			deleteProject(clone);
		} catch (Exception e){
			Log.error(e.getMessage(), e);
		}

		return null;
	}

	public static IStatus deleteProject(IProject project) {
		if (project != null && project.exists())
			try {
				project.delete(true, true, new NullProgressMonitor());
			} catch (CoreException e) {
				Log.error("Could not delete project", e);
				return new Status(Status.ERROR, Activator.PLUGIN_ID, "Could not delete project", e);
			}
		return Status.OK_STATUS;
	}
	
	private static IJavaProject createDriver(GraphElement method, String projectName, File instrumentedBytecode, IProject jimpleInstruments, boolean counters) throws CoreException, IOException {
		// create an empty project
		IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = workspaceRoot.getProject(projectName);
		project.create(null);
		project.open(null);
		
		// set the java nature
		IProjectDescription description = project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, null);
		
		// create the java project
		IJavaProject driverProject = JavaCore.create(project); 
		
		// add a binary folder
		IFolder binFolder = project.getFolder("bin");
		binFolder.create(false, true, null);
		driverProject.setOutputLocation(binFolder.getFullPath(), null);
		
		List<IClasspathEntry> entries = new ArrayList<IClasspathEntry>();
		IVMInstall vmInstall = JavaRuntime.getDefaultVMInstall();
		LibraryLocation[] locations = JavaRuntime.getLibraryLocations(vmInstall);
		for (LibraryLocation element : locations) {
			entries.add(JavaCore.newLibraryEntry(element.getSystemLibraryPath(), null, null));
		}
		
		// add instrumented bytecode to the classpath
		File libsDirectory = new File(project.getLocation().toFile().getAbsolutePath() + File.separatorChar + "lib");
		libsDirectory.mkdirs();
		File bytecodeJar = new File(libsDirectory.getAbsolutePath() + File.separatorChar + "instrumented-bytecode.jar");
		instrumentedBytecode.renameTo(bytecodeJar);
		instrumentedBytecode = bytecodeJar;
		String instrumentedBytecodeCanonicalPath = instrumentedBytecode.getCanonicalPath();
		String projectCanonicalPath = driverProject.getProject().getLocation().toFile().getCanonicalPath();
		String instrumentedBytecodeBasePath = instrumentedBytecodeCanonicalPath.substring(instrumentedBytecodeCanonicalPath.indexOf(projectCanonicalPath));
		String instrumentedBytecodeParentCanonicalPath = instrumentedBytecode.getCanonicalPath();
		String instrumentedBytecodeParentBasePath = instrumentedBytecodeParentCanonicalPath.substring(instrumentedBytecodeParentCanonicalPath.indexOf(projectCanonicalPath));
		entries.add(JavaCore.newLibraryEntry(new Path(instrumentedBytecodeBasePath), null, new Path(instrumentedBytecodeParentBasePath)));
		
		File newLibsDirectory = new File(driverProject.getProject().getLocation().toFile().getAbsolutePath() + File.separatorChar + "lib");
		newLibsDirectory.mkdirs();
		for(File jar : new File(jimpleInstruments.getLocation().toFile().getAbsolutePath() + File.separatorChar + "lib").listFiles()){
			if(jar.getName().endsWith(".jar")){
				File copiedJar = new File(newLibsDirectory.getAbsolutePath() + File.separatorChar + jar.getName());
				Files.copy(jar.toPath(), copiedJar.toPath());
				String copiedJarCanonicalPath = copiedJar.getCanonicalPath();
				String driverProjectCanonicalPath = driverProject.getProject().getLocation().toFile().getCanonicalPath();
				String copiedJarBasePath = copiedJarCanonicalPath.substring(copiedJarCanonicalPath.indexOf(driverProjectCanonicalPath));
				String copiedJarParentCanonicalPath = instrumentedBytecode.getCanonicalPath();
				String copiedJarParentBasePath = copiedJarParentCanonicalPath.substring(copiedJarParentCanonicalPath.indexOf(driverProjectCanonicalPath));
				entries.add(JavaCore.newLibraryEntry(new Path(copiedJarBasePath), null, new Path(copiedJarParentBasePath)));
			}
		}
		
		// add libs to project class path
		driverProject.setRawClasspath(entries.toArray(new IClasspathEntry[entries.size()]), null);
		
		// add a source folder
		IFolder sourceFolder = project.getFolder("src");
		sourceFolder.create(false, true, null);

		// add source folder to compilation entries
		IPackageFragmentRoot root = driverProject.getPackageFragmentRoot(sourceFolder);
		IClasspathEntry[] oldEntries = driverProject.getRawClasspath();
		IClasspathEntry[] newEntries = new IClasspathEntry[oldEntries.length + 1];
		System.arraycopy(oldEntries, 0, newEntries, 0, oldEntries.length);
		newEntries[oldEntries.length] = JavaCore.newSourceEntry(root.getPath());
		driverProject.setRawClasspath(newEntries, null);
		
		// create a new package
		IPackageFragment pkg = driverProject.getPackageFragmentRoot(sourceFolder).createPackageFragment("driver", false, null);
		
		// write the class file for the driver
		StringBuffer buffer = new StringBuffer();
		
		buffer.append("package " + pkg.getElementName() + ";\n");
		buffer.append("\n");
		if(counters){
			buffer.append("import tca.instrumentation.TCA_Counter;\n");
		} else {
			buffer.append("import tca.instrumentation.TCA_Timer;\n");
		}
		buffer.append("\n");
		buffer.append("@SuppressWarnings({\"rawtypes\"})\n");
		buffer.append("public class Driver {\n");
		buffer.append("\n");
		buffer.append("	// change total work units to increase or decrease \n");
		buffer.append("	// the number of collected data points\n");
		buffer.append("	private static final int TOTAL_WORK_TASKS = 100;\n");
		buffer.append("	\n");
		buffer.append("	public static void main(String[] args) throws Exception {\n");
		buffer.append("		for(int i=1; i<=TOTAL_WORK_TASKS; i++){\n");
		if(counters){
			buffer.append("			TCA_Counter.setSize(i);\n");
		} else {
			buffer.append("			TCA_Timer.setSize(i);\n");
		}
		buffer.append("			Object[] parameters = getWorkload(i);\n");
		buffer.append("			" + createMethodDriverCallsite(method) + "\n");
		buffer.append("		}\n");
		if(counters){
			buffer.append("		tca.TCA.plotRegression(\"" + getQualifiedName(method) + " Profile\", TOTAL_WORK_TASKS);\n");
		} else {
			buffer.append("		tca.TCA.plotRaw(\"" + getQualifiedName(method) + " Profile\", TOTAL_WORK_TASKS);\n");
		}
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	private static Object[] getWorkload(int size){\n");
		buffer.append("		return null; // TODO: specify work task for a given task size \n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("}\n");
		pkg.createCompilationUnit("Driver.java", buffer.toString(), false, null);
		
		addHelperClasses(driverProject, sourceFolder);
		
		return driverProject;
	}
	
	private static void addHelperClasses(IJavaProject driverProject, IFolder sourceFolder) throws JavaModelException {
		IPackageFragment pkg = driverProject.getPackageFragmentRoot(sourceFolder).createPackageFragment("tca", false, null);
		
		// write the class file for the driver
		StringBuffer buffer = new StringBuffer();
		
		buffer.append("package tca;\n");
		buffer.append("\n");
		buffer.append("import java.util.ArrayList;\n");
		buffer.append("\n");
		buffer.append("public class Measurements {\n");
		buffer.append("\n");
		buffer.append("	public static class Measurement {\n");
		buffer.append("		public int size;\n");
		buffer.append("		public long value;\n");
		buffer.append("\n");
		buffer.append("		public Measurement(int size, long value) {\n");
		buffer.append("			this.size = size;\n");
		buffer.append("			this.value = value;\n");
		buffer.append("		}\n");
		buffer.append("\n");
		buffer.append("		public double getLogSize() {\n");
		buffer.append("			return Math.log(size) / Math.log(2.0); // binary log\n");
		buffer.append("		}\n");
		buffer.append("\n");
		buffer.append("		public double getLogValue() {\n");
		buffer.append("			return Math.log(value) / Math.log(2.0); // binary log\n");
		buffer.append("		}\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	private ArrayList<Measurement> measurements = new ArrayList<Measurement>();\n");
		buffer.append("	\n");
		buffer.append("	public void add(int size, long value){\n");
		buffer.append("		if(value > 0){\n");
		buffer.append("			measurements.add(new Measurement(size, value));\n");
		buffer.append("		}\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	public int getNumMeasurements(){\n");
		buffer.append("		return measurements.size();\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	public ArrayList<Measurement> getMeasurements(){\n");
		buffer.append("		return measurements;\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("}\n");
		pkg.createCompilationUnit("Measurements.java", buffer.toString(), false, null);
		
		buffer = new StringBuffer();
		buffer.append("package tca;\n");
		buffer.append("\n");
		buffer.append("import java.text.DecimalFormat;\n");
		buffer.append("\n");
		buffer.append("import org.jfree.chart.ChartFactory;\n");
		buffer.append("import org.jfree.chart.JFreeChart;\n");
		buffer.append("import org.jfree.chart.annotations.XYLineAnnotation;\n");
		buffer.append("import org.jfree.chart.plot.PlotOrientation;\n");
		buffer.append("import org.jfree.chart.plot.XYPlot;\n");
		buffer.append("import org.jfree.data.xy.XYDataItem;\n");
		buffer.append("import org.jfree.data.xy.XYSeries;\n");
		buffer.append("import org.jfree.data.xy.XYSeriesCollection;\n");
		buffer.append("import org.jfree.ui.RectangleInsets;\n");
		buffer.append("\n");
		buffer.append("import tca.Measurements.Measurement;\n");
		buffer.append("\n");
		buffer.append("public class RegressionPlotChart extends Chart {\n");
		buffer.append("\n");
		buffer.append("	public RegressionPlotChart(String title, Measurements measurements) {\n");
		buffer.append("		super(title, measurements);\n");
		buffer.append("	}\n");
		buffer.append("\n");
		buffer.append("	@Override\n");
		buffer.append("	public JFreeChart getChart() {\n");
		buffer.append("		\n");
		buffer.append("		int n = measurements.getNumMeasurements();\n");
		buffer.append("		double[] x = new double[n];\n");
		buffer.append("		double[] y = new double[n];\n");
		buffer.append("		\n");
		buffer.append("		for(int i=0; i<n; i++){\n");
		buffer.append("			x[i] = measurements.getMeasurements().get(i).getLogSize();\n");
		buffer.append("			y[i] = measurements.getMeasurements().get(i).getLogValue();\n");
		buffer.append("		}\n");
		buffer.append("		\n");
		buffer.append("		double[] result = regress(x, y, n);\n");
		buffer.append("		double intercept = result[0];\n");
		buffer.append("		double slope = result[1];\n");
		buffer.append("		double r2 = result[2];\n");
		buffer.append("		\n");
		buffer.append("		XYSeriesCollection dataset = new XYSeriesCollection();\n");
		buffer.append("		XYSeries series = new XYSeries(\"Measurements\");\n");
		buffer.append("		for(Measurement measurement : measurements.getMeasurements()){\n");
		buffer.append("			series.add(new XYDataItem(measurement.getLogSize(), measurement.getLogValue()));\n");
		buffer.append("		}\n");
		buffer.append("		dataset.addSeries(series);\n");
		buffer.append("		\n");
		buffer.append("		DecimalFormat decimalFormat = new DecimalFormat(\"0.00\");\n");
		buffer.append("		\n");
		buffer.append("		JFreeChart chart = ChartFactory.createScatterPlot(title + \", R2=\" + decimalFormat.format(r2), // title\n");
		buffer.append("														  \"Log(Workload Size)\", // x-axis label\n");
		buffer.append("														  \"Log(Iteration Count)\",  // y-axis label\n");
		buffer.append("														  dataset, // data\n");
		buffer.append("														  PlotOrientation.VERTICAL, // orientation\n");
		buffer.append("														  showLegend, // create legend\n");
		buffer.append("														  true, // generate tooltips\n");
		buffer.append("														  false); // generate urls\n");
		buffer.append("		\n");
		buffer.append("		chart.setBackgroundPaint(java.awt.Color.white);\n");
		buffer.append("		XYPlot plot = (XYPlot) chart.getPlot();\n");
		buffer.append("		plot.setBackgroundPaint(java.awt.Color.lightGray);\n");
		buffer.append("		plot.setDomainGridlinePaint(java.awt.Color.white);\n");
		buffer.append("		plot.setRangeGridlinePaint(java.awt.Color.white);\n");
		buffer.append("		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));\n");
		buffer.append("		plot.setDomainCrosshairVisible(true);\n");
		buffer.append("		plot.setRangeCrosshairVisible(true);\n");
		buffer.append("		\n");
		buffer.append("		\n");
		buffer.append("//		// lazy points...not working?\n");
		buffer.append("//		double x1 = 0;\n");
		buffer.append("//		double y1 = intercept;\n");
		buffer.append("//		double x2 = 1;\n");
		buffer.append("//		double y2 = slope + intercept;\n");
		buffer.append("		\n");
		buffer.append("		// add the trendline\n");
		buffer.append("		double xlb = 0;\n");
		buffer.append("		double xub = plot.getDomainAxis().getRange().getUpperBound();\n");
		buffer.append("		double ylb = 0;\n");
		buffer.append("		double yub = plot.getRangeAxis().getRange().getUpperBound();\n");
		buffer.append("		double x1 = getX(ylb, slope, intercept);\n");
		buffer.append("		double y1 = getY(xlb, slope, intercept);\n");
		buffer.append("		double x2 = getX(yub, slope, intercept);\n");
		buffer.append("		double y2 = getY(xub, slope, intercept);\n");
		buffer.append("		XYLineAnnotation annotation = new XYLineAnnotation(x1, y1, x2, y2);\n");
		buffer.append("		annotation.setToolTipText(\"y=\" + decimalFormat.format(slope) + \"*x + \" + decimalFormat.format(intercept));\n");
		buffer.append("		plot.addAnnotation(annotation);\n");
		buffer.append("		\n");
		buffer.append("		return chart;\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	// y=mx+b where m is the slope and b is the y-intercept\n");
		buffer.append("	private static double getY(double x, double slope, double intercept){\n");
		buffer.append("		double y = (slope * x) + intercept;\n");
		buffer.append("		return y;\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	// x = (y-b)/m where m is the slope and b is the y-intercept\n");
		buffer.append("	private static double getX(double y, double slope, double intercept){\n");
		buffer.append("		double x = (y-intercept)/slope;\n");
		buffer.append("		return x;\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	private static double[] regress(double[] x, double[] y, int n) {\n");
		buffer.append("		double[] res = new double[3];\n");
		buffer.append("		// first pass: read in data, compute xbar and ybar\n");
		buffer.append("		double sumx = 0, sumy = 0; // , sumx2 = 0.0; used only for error\n");
		buffer.append("									// analysis\n");
		buffer.append("		for (int i = 0; i < n; i++) {\n");
		buffer.append("			sumx += x[i];\n");
		buffer.append("			sumy += y[i];\n");
		buffer.append("			// sumx2 += x[i]*x[i];\n");
		buffer.append("		}\n");
		buffer.append("		double xbar = sumx / n;\n");
		buffer.append("		double ybar = sumy / n;\n");
		buffer.append("\n");
		buffer.append("		// second pass: compute summary statistics\n");
		buffer.append("		double xxbar = 0.0, yybar = 0.0, xybar = 0.0;\n");
		buffer.append("		for (int i = 0; i < n; i++) {\n");
		buffer.append("			xxbar += (x[i] - xbar) * (x[i] - xbar);\n");
		buffer.append("			yybar += (y[i] - ybar) * (y[i] - ybar);\n");
		buffer.append("			xybar += (x[i] - xbar) * (y[i] - ybar);\n");
		buffer.append("		}\n");
		buffer.append("		double beta1 = xybar / xxbar;\n");
		buffer.append("		double beta0 = ybar - beta1 * xbar;\n");
		buffer.append("		res[0] = beta0;\n");
		buffer.append("		res[1] = beta1;\n");
		buffer.append("\n");
		buffer.append("		double ssr = 0.0; // regression sum of squares\n");
		buffer.append("		for (int i = 0; i < n; i++) {\n");
		buffer.append("			double fit = beta1 * x[i] + beta0;\n");
		buffer.append("			// rss += (fit - y[i]) * (fit - y[i]);\n");
		buffer.append("			ssr += (fit - ybar) * (fit - ybar);\n");
		buffer.append("		}\n");
		buffer.append("		double R2 = ssr / yybar;\n");
		buffer.append("		res[2] = R2;\n");
		buffer.append("		return res;\n");
		buffer.append("	}\n");
		buffer.append("\n");
		buffer.append("}\n");
		
		pkg.createCompilationUnit("RegressionPlotChart.java", buffer.toString(), false, null);
		
		buffer = new StringBuffer();
		buffer.append("package tca;\n");
		buffer.append("\n");
		buffer.append("import javax.swing.JFrame;\n");
		buffer.append("import javax.swing.SwingUtilities;\n");
		buffer.append("\n");
		buffer.append("import org.jfree.chart.ChartPanel;\n");
		buffer.append("import org.jfree.chart.JFreeChart;\n");
		buffer.append("\n");
		buffer.append("public abstract class Chart {\n");
		buffer.append("	\n");
		buffer.append("	protected String title;\n");
		buffer.append("	protected boolean showLegend = true;\n");
		buffer.append("	protected boolean showLabels = false;\n");
		buffer.append("	protected Measurements measurements;\n");
		buffer.append("	\n");
		buffer.append("	protected Chart(String title, Measurements measurements){\n");
		buffer.append("		this.title = title;\n");
		buffer.append("		this.measurements = measurements;\n");
		buffer.append("	}\n");
		buffer.append("\n");
		buffer.append("	public abstract JFreeChart getChart();\n");
		buffer.append("	\n");
		buffer.append("	public boolean showLegendEnabled(){\n");
		buffer.append("		return showLegend;\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	public void enableShowLegend(boolean showLegend){\n");
		buffer.append("		this.showLegend = showLegend;\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	public boolean showLabelsEnabled(){\n");
		buffer.append("		return showLabels;\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	public void enableShowLabels(boolean showLabels){\n");
		buffer.append("		this.showLabels = showLabels;\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	public void show(){\n");
		buffer.append("		SwingUtilities.invokeLater(new Runnable() {\n");
		buffer.append("            public void run() {\n");
		buffer.append("                JFrame frame = new JFrame(\"Time Complexity Analyzer\");\n");
		buffer.append("                frame.setSize(600, 400);\n");
		buffer.append("                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);\n");
		buffer.append("                frame.setVisible(true);\n");
		buffer.append("                ChartPanel chartPanel = new ChartPanel(getChart());\n");
		buffer.append("                frame.getContentPane().add(chartPanel);\n");
		buffer.append("            }\n");
		buffer.append("        });\n");
		buffer.append("	}\n");
		buffer.append("\n");
		buffer.append("}\n");
		
		pkg.createCompilationUnit("Chart.java", buffer.toString(), false, null);
		
		buffer = new StringBuffer();
		buffer.append("package tca;\n");
		buffer.append("\n");
		buffer.append("import java.util.Date;\n");
		buffer.append("\n");
		buffer.append("import org.jfree.chart.ChartFactory;\n");
		buffer.append("import org.jfree.chart.JFreeChart;\n");
		buffer.append("import org.jfree.chart.axis.ValueAxis;\n");
		buffer.append("import org.jfree.chart.plot.XYPlot;\n");
		buffer.append("import org.jfree.chart.renderer.xy.XYItemRenderer;\n");
		buffer.append("import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;\n");
		buffer.append("import org.jfree.data.time.Millisecond;\n");
		buffer.append("import org.jfree.data.time.TimeSeries;\n");
		buffer.append("import org.jfree.data.time.TimeSeriesCollection;\n");
		buffer.append("import org.jfree.data.xy.XYDataset;\n");
		buffer.append("import org.jfree.ui.RectangleInsets;\n");
		buffer.append("\n");
		buffer.append("import tca.Measurements.Measurement;\n");
		buffer.append("\n");
		buffer.append("public class TimePlotChart extends Chart {\n");
		buffer.append("\n");
		buffer.append("	public TimePlotChart(String title, Measurements measurements) {\n");
		buffer.append("		super(title, measurements);\n");
		buffer.append("	}\n");
		buffer.append("\n");
		buffer.append("	@Override\n");
		buffer.append("	public JFreeChart getChart() {\n");
		buffer.append("		return createXYChart(createXYDataset(measurements, \"measurements\"));\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	private JFreeChart createXYChart(XYDataset dataset) {\n");
		buffer.append("		JFreeChart chart = ChartFactory.createTimeSeriesChart(title, // title\n");
		buffer.append("				\"Size\", // x-axis label\n");
		buffer.append("				\"Time\", // y-axis label\n");
		buffer.append("				dataset, // data\n");
		buffer.append("				showLegend, // create legend?\n");
		buffer.append("				true, // generate tooltips?\n");
		buffer.append("				false // generate URLs?\n");
		buffer.append("				);\n");
		buffer.append("\n");
		buffer.append("		chart.setBackgroundPaint(java.awt.Color.white);\n");
		buffer.append("\n");
		buffer.append("		XYPlot plot = (XYPlot) chart.getPlot();\n");
		buffer.append("		plot.setBackgroundPaint(java.awt.Color.lightGray);\n");
		buffer.append("		plot.setDomainGridlinePaint(java.awt.Color.white);\n");
		buffer.append("		plot.setRangeGridlinePaint(java.awt.Color.white);\n");
		buffer.append("		plot.setAxisOffset(new RectangleInsets(5.0, 5.0, 5.0, 5.0));\n");
		buffer.append("		plot.setDomainCrosshairVisible(true);\n");
		buffer.append("		plot.setRangeCrosshairVisible(true);\n");
		buffer.append("		\n");
		buffer.append("		// hide the Y axis range\n");
		buffer.append("		ValueAxis range = plot.getRangeAxis();\n");
		buffer.append("		range.setVisible(false);\n");
		buffer.append("\n");
		buffer.append("		XYItemRenderer r = plot.getRenderer();\n");
		buffer.append("		if (r instanceof XYLineAndShapeRenderer) {\n");
		buffer.append("			XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;\n");
		buffer.append("			renderer.setBaseShapesVisible(true);\n");
		buffer.append("			renderer.setBaseShapesFilled(true);\n");
		buffer.append("			renderer.setDrawSeriesLineAsPath(true);\n");
		buffer.append("		}\n");
		buffer.append("\n");
		buffer.append("		return chart;\n");
		buffer.append("	}\n");
		buffer.append("\n");
		buffer.append("	private XYDataset createXYDataset(Measurements measurements, String seriesName) {\n");
		buffer.append("\n");
		buffer.append("		TimeSeriesCollection dataset = new TimeSeriesCollection();\n");
		buffer.append("		TimeSeries s = new TimeSeries(seriesName);\n");
		buffer.append("		\n");
		buffer.append("		for(Measurement measurement : measurements.getMeasurements()){\n");
		buffer.append("			s.add(new Millisecond(new Date(measurement.value)), measurement.size);\n");
		buffer.append("		}\n");
		buffer.append("		\n");
		buffer.append("		return dataset;\n");
		buffer.append("	}\n");
		buffer.append("\n");
		buffer.append("}\n");
		
		pkg.createCompilationUnit("TimePlotChart.java", buffer.toString(), false, null);
		
		buffer = new StringBuffer();
		buffer.append("package tca;\n");
		buffer.append("\n");
		buffer.append("import java.io.IOException;\n");
		buffer.append("import java.util.HashMap;\n");
		buffer.append("\n");
		buffer.append("import tca.instrumentation.TCA_Counter;\n");
		buffer.append("\n");
		buffer.append("public class TCA {\n");
		buffer.append("\n");
		buffer.append("	public static void plotRaw(final String title, final int TOTAL_WORK_TASKS){\n");
		buffer.append("		// TODO: implement\n");
		buffer.append("	}\n");
		buffer.append("	\n");
		buffer.append("	public static void plotRegression(final String title, final int TOTAL_WORK_TASKS) throws IOException, InterruptedException {\n");
		buffer.append("		Measurements measurements = new Measurements();\n");
		buffer.append("		for (int i = 1; i <= TOTAL_WORK_TASKS; i++) {\n");
		buffer.append("			HashMap<String, Long> counters = TCA_Counter.getCountersForSize(i);\n");
		buffer.append("			long sum = 0;\n");
		buffer.append("			for (Long l : counters.values()) {\n");
		buffer.append("				sum += l;\n");
		buffer.append("			}\n");
		buffer.append("			measurements.add(i, sum);\n");
		buffer.append("		}\n");
		buffer.append("		\n");
		buffer.append("		RegressionPlotChart scatterPlot = new RegressionPlotChart(title, measurements);\n");
		buffer.append("		scatterPlot.show();\n");
		buffer.append("	}\n");
		buffer.append("\n");
		buffer.append("}\n");
		
		pkg.createCompilationUnit("TCA.java", buffer.toString(), false, null);
	}

	/**
	 * Creates a string representing a source code callsite to the given method with an Object array for parameters
	 * @param method
	 * @return
	 */
	public static String createMethodDriverCallsite(GraphElement method){
		String result = getQualifiedName(method);
		result += "(";
		HashMap<Integer,GraphElement> parameterTypes = new HashMap<Integer,GraphElement>();
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		Q params = containsEdges.forwardStep(Common.toQ(method)).nodesTaggedWithAny(XCSG.Parameter);
		for(GraphElement param : params.eval().nodes()){
			int paramIndex = (Integer) param.getAttr(XCSG.parameterIndex) + 1;
			GraphElement paramType = typeOfEdges.successors(Common.toQ(param)).eval().nodes().getFirst();
			parameterTypes.put(paramIndex, paramType);
		}
		String prefix = "";
		for(int i=1; i<=parameterTypes.size(); i++){
			GraphElement paramType = parameterTypes.get(i);
			result += prefix + "(" + getQualifiedName(paramType) + ") " + "parameters[" + (i-1) + "]";
			prefix = ", ";
		}
		result += ");";
		return result;
	}
	
	// helper method to get a qualified name of a method
	public static String getQualifiedName(GraphElement node){
		String name = node.getAttr(XCSG.name).toString();
		// qualify the label
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		GraphElement parent = containsEdges.predecessors(Common.toQ(node)).eval().nodes().getFirst();
		while(parent != null && !parent.tags().contains(XCSG.Project) && !parent.tags().contains(XCSG.Library)){
			// skip adding qualified part for default package
			if(!(parent.tags().contains(XCSG.Package) && parent.getAttr(XCSG.name).toString().equals(""))){
				name = parent.getAttr(XCSG.name).toString() + "." + name;
			}
			parent = containsEdges.predecessors(Common.toQ(parent)).eval().nodes().getFirst();
		}
		return name;
	}

}
