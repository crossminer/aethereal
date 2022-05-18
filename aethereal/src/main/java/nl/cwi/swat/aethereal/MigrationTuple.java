package nl.cwi.swat.aethereal;

import java.util.List;

import org.apache.commons.lang.StringUtils;

enum ChangeType{
	UPDATED, ADDED, REMOVED
}

public class MigrationTuple {
	
	private String coordinatesV1;
	
	private String coordinatesV2;
	
	private String caller;
	
	private String called;
	
	private List<String> v1Body;
	
	private List<String> v2Body;
	
	private String affectedLib;
	
	private ChangeType change;
	
	
	public String getCoordinatesV1() {
		return coordinatesV1;
	}
	public void setCoordinatesV1(String coordinatesV1) {
		this.coordinatesV1 = coordinatesV1;
	}
	public String getCoordinatesV2() {
		return coordinatesV2;
	}
	public void setCoordinatesV2(String coordinatesV2) {
		this.coordinatesV2 = coordinatesV2;
	}
	public String getCaller() {
		return caller;
	}
	public void setCaller(String caller) {
		this.caller = caller;
	}
	public String getCalled() {
		return called;
	}
	public void setCalled(String called) {
		this.called = called;
	}
	public List<String> getV1Body() {
		return v1Body;
	}
	public void setV1Body(List<String> v1Body) {
		this.v1Body = v1Body;
	}
	public List<String> getV2Body() {
		return v2Body;
	}
	public void setV2Body(List<String> v2Body) {
		this.v2Body = v2Body;
	}
	public String getAffectedLib() {
		return affectedLib;
	}
	public void setAffectedLib(String affectedLib) {
		this.affectedLib = affectedLib;
	}
	public ChangeType getChange() {
		return change;
	}
	public void setChange(ChangeType change) {
		this.change = change;
	}
	
	@Override
	public String toString() {
		return String.format("C1:%s\n"
				+ "C2:%s\n"
				+ "\tChange Type:%s\n"
				+ "\tlib:%s\n"
				+ "\tcalled:%s\n"
				+ "\tcaller:%s\n"
				+ "\t\tC1 Body:\n\t\t%s\n"
				+ "\t\tC2 Body:\n\t\t\t%s",
				coordinatesV1, coordinatesV2, change, affectedLib, called, caller, StringUtils.join(v1Body,"\n\t\t\t"),StringUtils.join(v2Body,"\n\t\t\t"));
	}
}
