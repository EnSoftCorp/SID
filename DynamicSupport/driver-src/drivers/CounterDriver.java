package drivers;

import tca.instrumentation.TCA_Counter;

@SuppressWarnings({"rawtypes"})
public class CounterDriver {

	// change total work units to increase or decrease 
	// the number of collected data points
	private static final int TOTAL_WORK_TASKS = 100;
	
	public static void main(String[] args) throws Exception {
		for(int i=1; i<=TOTAL_WORK_TASKS; i++){
			TCA_Counter.setSize(i);
			Object[] parameters = getWorkload(i);
			// TODO: call method with parameters for a given workload
		}
		tca.TCA.plotRegression("Counter Workload Profile", TOTAL_WORK_TASKS);
	}
	
	private static Object[] getWorkload(int size){
		return null; // TODO: implement: return workload parameters for given size
	}
	
}