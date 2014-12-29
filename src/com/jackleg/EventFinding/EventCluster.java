package com.jackleg.EventFinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

/**
 * 이벤트 발견을 위한 클러스터.
 */
public class EventCluster extends Cluster {
	/**
	 * constructor
	 */
	public EventCluster(int id, Author author) {
		super(id, author);
	}
	
	public EventCluster(int id, Author author, ArrayList<ImageEntity> images) {
		super(id, author);
		this.images = images;
	}
	
	/**
	 * 이 클러스터가 event인지 여부를 파악.
	 * 외국에서 찍은 사진이 1장이라도 있거나 전체 사진이 11장 이상이면서 10분 초과 범위에서 찍혔으면 무조건 이벤트
	 * 사진이 3장 이하면 무조건 이벤트 탈락
	 * 홈타운의 도시레벨(서울특별시, 경기도 등)을 벗어난 곳일 경우 7장 이상이면 이벤트
	 * 그 외의 경우 모두 이벤트 탈락
	 * @return 주어진 event 후보가 이벤트라면 true, 그렇지 않으면 false.
	 */
	public boolean isEvent() {
		int abroadCount            = 0;
		int outOfHometownCityCount = 0;
		int totalCount             = getImagesCount();
		
		for (ImageEntity image : images) {
			if(author.isOutOfHometownCity(image)) outOfHometownCityCount++;
			if(author.isAbroad(image))            abroadCount++;
		}
		long durationInSec = EfUtility.calculateTimeDiffInSec(getFirstTime(), getLastTime());

		if(abroadCount >= 1) return true;
		if(totalCount > 10 && durationInSec > 10 * 60) return true;
		if(totalCount <= 3) return false;
		if(totalCount == outOfHometownCityCount && totalCount >= 7) return true;
		return false;
	}
	
	/**
	 * 이 클러스터를 sub 클러스터로 나누어 리스트에 담아 반환한다.
	 * 현재는 1시간을 기준으로 sub 클러스터를 나눈다.
	 * @return 서브 클러스터의 리스트. 이미지가 없거나, split되지 않았다면 null.
	 */
	public ArrayList<EventCluster> splitEventCluster() {
		ArrayList<ArrayList<ImageEntity>> splittedImages = splitByInterval(EfUtility.HOUR1_IN_SECONDS);
		if(splittedImages == null) return null;
		
		ArrayList<EventCluster> subClusters = new ArrayList<EventCluster>();

		int cid = 0;
		for(ArrayList<ImageEntity> subimages : splittedImages) {
			EventCluster subCluster = new EventCluster(cid, author, subimages);
			subClusters.add(subCluster);
		}
		
		return subClusters;
	}
	
	/**
	 * 이 클러스터의 대표 지명 이름을 반환한다.
	 * 이 클러스터에 속한 사진이 hometown에 있다면 address componet의 1, 2번째 값을, 그렇지 않으면 나라 값의 count를 구해, 가장 많은 값을 사용한다.
	 */
	public String getRepLocation() {
		HashMap<String, Integer> labelMap = new HashMap<String, Integer>();
		
		for(ImageEntity image : images) {
			String label = null;
			if(author.isAbroad(image)) {
				label = image.getCountry();
			}
			else {
				String[] acArray = image.getAddressComponents();
				if(acArray == null) continue;
				
				label = acArray[0] + " " + acArray[1]; 
			}
			
			if(labelMap.containsKey(label)) labelMap.put(label, labelMap.get(label) + 1);
			else                            labelMap.put(label, 1);
		}
		
		List<Entry<String, Integer>> sortedList = EfUtility.sortMapByValue(labelMap, true);
		
		if(sortedList.size() == 0) return "";
		if(sortedList.size() == 1) return sortedList.get(0).getKey() + "에서";
		return sortedList.get(0).getKey() + " 등에서";
	}
	
	/**
	 * event의 기간을 string으로 표시.
	 */
	public String getRange() {
		SimpleDateFormat dateSdf = new SimpleDateFormat("yyyy/MM/dd");
		
		String from = dateSdf.format(getFirstTime());
		String to   = dateSdf.format(getLastTime());
		
		if(from.equals(to)) return from + "에";
		else                return from + "에서 " + to +"까지";
	}
}
