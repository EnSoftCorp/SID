package tca;

import java.util.ArrayList;

public class Measurements {

	public static class Measurement {
		public int size;
		public long value;

		public Measurement(int size, long value) {
			this.size = size;
			this.value = value;
		}

		public double getSize() {
			return size;
		}

		public double getValue() {
			return value;
		}
		
		public double getLogSize() {
			return Math.log(size) / Math.log(2.0); // binary log
		}

		public double getLogValue() {
			return Math.log(value) / Math.log(2.0); // binary log
		}
	}
	
	private ArrayList<Measurement> measurements = new ArrayList<Measurement>();
	
	public void add(int size, long value){
		if(value > 0){
			measurements.add(new Measurement(size, value));
		}
	}
	
	public int getNumMeasurements(){
		return measurements.size();
	}
	
	public ArrayList<Measurement> getMeasurements(){
		return measurements;
	}
	
}
