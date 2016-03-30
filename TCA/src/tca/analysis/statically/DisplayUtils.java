package tca.analysis.statically;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.highlight.Highlighter;
import com.ensoftcorp.atlas.core.markup.MarkupFromH;
import com.ensoftcorp.atlas.core.query.Attr.Edge;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.CommonQueries;
import com.ensoftcorp.atlas.ui.viewer.graph.DisplayUtil;
import com.ensoftcorp.atlas.ui.viewer.graph.SaveUtil;

/**
 * A set of helper utilities for some common display related methods
 * 
 * @author Ben Holland
 */
public class DisplayUtils {

	private final static long LARGE_GRAPH_WARNING = 100;

	/**
	 * Opens a display prompt alerting the user of the error and offers the
	 * ability to copy a stack trace to the clipboard
	 * 
	 * @param t the throwable to grab stack trace from
	 * @param message the message to display
	 */
	public static void showError(final Throwable t, final String message) {
		final Display display = Display.getDefault();
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				MessageBox mb = new MessageBox(Display.getDefault().getActiveShell(), SWT.ICON_ERROR | SWT.NO | SWT.YES);
				mb.setText("Alert");
				StringWriter errors = new StringWriter();
				t.printStackTrace(new PrintWriter(errors));
				String stackTrace = errors.toString();
				mb.setMessage(message + "\n\nWould you like to copy the stack trace?");
				int response = mb.open();
				if (response == SWT.YES) {
					StringSelection stringSelection = new StringSelection(stackTrace);
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(stringSelection, stringSelection);
				}
			}
		});
	}
	
	/**
	 * Helper method for logging stack traces to a file
	 * @param t
	 * @param log
	 */
	public static void logErrorToFile(Throwable t, File log, boolean append){
		try {
			FileWriter fw = new FileWriter(log, append);
			StringWriter errors = new StringWriter();
			t.printStackTrace(new PrintWriter(errors));
			String stackTrace = errors.toString();
			fw.write("" + System.currentTimeMillis() + "\n");
			fw.write(stackTrace + "\n\n");
			fw.close();
		} catch (Exception e){
			// what do you do when the back up to the back up fails?
			e.printStackTrace();
		}
	}
	
	/**
	 * Opens a display prompt showing a message
	 * @param message
	 */
	public static void showMessage(final String message){
		final Display display = Display.getDefault();
		display.syncExec(new Runnable() {
			@Override
			public void run() {
				MessageBox mb = new MessageBox(Display.getDefault().getActiveShell(), SWT.ICON_INFORMATION | SWT.OK);
				mb.setText("Message");
				mb.setMessage(message);
				mb.open();
			}
		});
	}

	/**
	 * A show method for a single graph element
	 * Defaults to extending and no highlighting
	 * @param ge The GraphElement to show
	 * @param title A title to indicate the graph content
	 */
	public static void show(final GraphElement ge, final String title) {
		show(Common.toQ(ge), title);
	}
	
	/**
	 * A show method for a single graph element
	 * @param ge The GraphElement to show
	 * @param h An optional highlighter, set to null otherwise
	 * @param extend A boolean to define if the graph should be extended (typical use is true)
	 * @param title A title to indicate the graph content
	 */
	public static void show(final GraphElement ge, final Highlighter h, final boolean extend, final String title) {
		show(Common.toQ(ge), h, extend, title);
	}
	
	/**
	 * Shows a graph inside Atlas
	 * Defaults to extending and no highlighting
	 * @param ge The GraphElement to show
	 * @param title A title to indicate the graph content
	 */
	public static void show(final Q q, final String title) {
		show(q, null, true, title);
	}
	
	/**
	 * Shows a graph inside Atlas
	 * 
	 * @param q The query to show
	 * @param h An optional highlighter, set to null otherwise
	 * @param extend A boolean to define if the graph should be extended (typical use is true)
	 * @param title A title to indicate the graph content
	 */
	public static void show(final Q q, final Highlighter h, final boolean extend, final String title) {
		final Display display = Display.getDefault();
		display.syncExec(new Runnable() {
			public void run() {
				long graphSize = CommonQueries.nodeSize(q);
				boolean showGraph = false;

				if (graphSize > LARGE_GRAPH_WARNING) {
					MessageBox mb = new MessageBox(new Shell(display), SWT.ICON_QUESTION | SWT.YES | SWT.NO);
					mb.setText("Warning");
					mb.setMessage("The graph you are about to display has " + graphSize + " nodes.  " 
							+ "Displaying large graphs may cause Eclipse to become unresponsive." 
							+ "\n\nDo you want to continue?");
					int response = mb.open();
					if (response == SWT.YES) {
						showGraph = true; // user says let's do it!!
					}
				} else {
					// graph is small enough to display
					showGraph = true;
				}

				if (showGraph) {
					Q displayExpr = extend ? Common.extend(q, Edge.DECLARES) : q;
					DisplayUtil.displayGraph(displayExpr.eval(), (h != null ? h : new Highlighter()), title);
				}
			}
		});
	}

	/**
	 * Saves the given Q to a file as an image
	 * 
	 * @param q
	 * @param h
	 * @param extend
	 * @param title
	 * @throws InterruptedException
	 */
	public static void save(Q q, Highlighter h, boolean extend, String title, File directory) throws InterruptedException {
		if (!directory.isDirectory()) {
			throw new IllegalArgumentException("File must be a directory");
		}
		if (h == null){
			h = new Highlighter();
		}
		File outputFile = new File(directory.getAbsolutePath() + File.separatorChar + title.toLowerCase().replaceAll("\\s+", " ").replaceAll(" ", "_") + ".png");
		if (extend){
			q = q.containers();
		}
		org.eclipse.core.runtime.jobs.Job job = SaveUtil.saveGraph(outputFile, q.eval(), new MarkupFromH(h));
		job.join(); // block until save is complete
	}

	/**
	 * Creates a qualified class name (stripping out dollar signs found in inner java class and Scala class representations)
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public static String formatClassName(Object object) {
		if(object instanceof Class){
			return ((Class) object).getName().replace("$", "");
		}
		return object.getClass().getPackage().toString().replace("package ", "") + "." + object.getClass().getSimpleName().replace("$", "");
	}

	public static void openFileInEclipseEditor(File file) {
		if (file.exists() && file.isFile()) {
			IFileStore fileStore = EFS.getLocalFileSystem().getStore(file.toURI());
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			try {
				IDE.openEditorOnFileStore(page, fileStore);
			} catch (PartInitException e) {
				showError(e, "Could not display file: " + file.getAbsolutePath());
			}
		} else {
			MessageBox mb = new MessageBox(Display.getDefault().getActiveShell(), SWT.OK);
			mb.setText("Alert");
			mb.setMessage("Could not find file: " + file.getAbsolutePath());
			mb.open();
		}
	}

}