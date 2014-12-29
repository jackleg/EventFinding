package com.jackleg.EventFinding;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import com.jackleg.EventFinding.EfUtility.Quantiles;

/**
 * 이미지의 집합을 나타내기 위한 클래스. 
 */
public class Cluster
	implements Comparable<Cluster>
{
	private int id;
	protected Author author;
	protected ArrayList<ImageEntity> images;
	
	/**
	 * constructor.
	 */
	public Cluster() {
		this(0, null);
	}

	/**
	 * constructor.
	 */
	public Cluster(int id, Author author) {
		this.id = id;
		this.author = author;
		this.images = null;
	}
	
	public int getId() { return id; }
	public ArrayList<ImageEntity> getImages() { return images; }
	public void setId(int id) { this.id = id; }

	/**
	 * index i번째 이미지를 반환.
	 * @param i 반환할 이미지 index.
	 * @return i번째 이미지. 이미지가 없으면 null.
	 */
	public ImageEntity getImage(int i) {
		if(images == null) return null;
		return images.get(i);
	}
	
	/**
	 * @return 이 클러스터에 속한 이미지들의 개수
	 */
	public int getImagesCount() {
		if(images == null) return 0;
		return images.size();
	}
	
	/**
	 * 이미지를 클러스터에 추가
	 * @param image 추가될 이미지
	 */
	public void addImage(ImageEntity image) {
		if(images == null) images = new ArrayList<ImageEntity>();
		images.add(image);
	}
	
	/**
	 * 주어진 이미지가 이 클러스터에 속해 있는지 여부를 판단.
	 * @return 이미지가 이 클러스터에 속해 있다면 true, 그렇지 않으면 false.
	 */
	public boolean contains(ImageEntity image) {
		if(images == null) return false;
		
		return images.contains(image);
	}
	
	/**
	 * 이 클러스터에 포함된 사진들 중 가장 빠른 시각을 반환
	 * @return 이 클러스터에 속한 사진들 중, 가장 빠른 시각. 사진이 없다면 null.
	 */
	public Date getFirstTime() {
		if(images == null || images.size() == 0) return null;
		return images.get(0).getDateTime();
	}
	
	/**
	 * 이 클러스터에 포함된 사진들 중 가장 늦은 시각을 반환
	 * @return 이 클러스터에 속한 사진들 중, 가장 늦은 시각. 사진이 없다면 null.
	 */
	public Date getLastTime() {
		if(images == null || images.size() == 0) return null;
		return images.get(images.size()-1).getDateTime();
	}
	
	/**
	 * 이 클러스터와 주어진 클러스터를 병합한다.
	 * @param cluster 병합할 클러스터.
	 */
	public void mergeCluster(Cluster cluster) {
		if(cluster.getImages() == null) return;
		for(ImageEntity image : cluster.getImages()) addImage(image);
		sortImages();
	}
	
	/**
	 * 클러스터의 이미지들을 시간순으로 정렬한다.
	 */
	public void sortImages() {
		Collections.sort(images);
	}
	
	/**
	 * 클러스터의 사진들을 shot interval을 기준으로 split해, 새로운 클러스터의 리스트를 반환한다.
	 * @param intervalThresholdInSec split하기 위한 interval threshold. in sec. 0을 받으면 IQR로 upper fence를 계산해 사용한다.
	 * @return split된 사진들의 리스트의 리스트.
	 */
	public ArrayList<ArrayList<ImageEntity>> splitByInterval(double intervalThresholdInSec) {
		sortImages();

		// intervalThreshold가 0이면 IQR 기법으로 upper fence를 계산한다.
		if(intervalThresholdInSec == 0.0) {
			// IQR 기법을 사용하기 위해서는 최소한 4개의 이미지가 있어야 함. 없다면 전체 이미지를 모두 포함하기 위해 max 값을 취함.
			if(images.size() < 4) intervalThresholdInSec = Double.MAX_VALUE;
			
			ArrayList<Double> diffList = new ArrayList<Double>();
			for(int i=1;i<images.size();i++) {
				// warning:
				// A: 29일 13시, B: 31일 10시인 경우, diffInDay는 2일이 되어야 함.
				// 이것을 계산할 때, B.getTime()-A.getTime() 으로 계산하면, 1.xxx가 되기 때문에 diffInDay가 1이 됨.
				// 로직을 변경할 일이 있을 경우 조심할 것.
				long diffInDay = EfUtility.calculateDiffDays(images.get(i-1).getDateTime(), images.get(i).getDateTime());
				diffList.add((double)diffInDay);
			}
		
			Quantiles quantiles = EfUtility.calculateQuantiles(diffList.toArray(new Double[0]));
			// 3rd quantile 값이 1.0보다 작거나 같은 경우는 대부분의 사진들이 같은 날, 혹은 매일 찍힌다는 것으로, 통계치를 사용하지 않고 한달을 사용한다.
			if(quantiles.q3 <= 1.0) intervalThresholdInSec = 30.0 * EfUtility.DAY1_IN_SECONDS;
			else                    intervalThresholdInSec = (quantiles.q3 + 1.5 * (quantiles.q3 - quantiles.q1)) * EfUtility.DAY1_IN_SECONDS;
		}
		
		// Date에서는 millisecond 기준으로 계산하므로 * 1000
		double intervalThreshold = intervalThresholdInSec * 1000;
		
		int startIndex = 0;
		int endIndex   = 0;
		ArrayList<ArrayList<ImageEntity>> resultList = new ArrayList<ArrayList<ImageEntity>>();
		
		for(int index=1;index<images.size();index++) {
			long interval = images.get(index).getDateTime().getTime() - images.get(index-1).getDateTime().getTime();
			if(interval <= intervalThreshold) { endIndex = index; }
			else { // interval이 threshold보다 높다는 것은, 새로운 클러스터의 시작이라는 뜻. 이제까지의 묶음으로 클러스터를 만들어 추가한다.
				ArrayList<ImageEntity> currentCluster = new ArrayList<ImageEntity>();
				for(int imageIndex=startIndex; imageIndex<=endIndex;imageIndex++) currentCluster.add(images.get(imageIndex));
				resultList.add(currentCluster);
				
				endIndex = startIndex = index;
			}
		}
		
		// for문을 돌고 난 후 남은 마지막 이미지들
		ArrayList<ImageEntity> currentCluster = new ArrayList<ImageEntity>();
		for(int imageIndex=startIndex; imageIndex<=endIndex; imageIndex++) currentCluster.add(images.get(imageIndex));
		resultList.add(currentCluster);
		
		return resultList;
	}
	
	/**
	 * 사진을 찍은 날짜 수를 반환
	 * @return 이 클러스터에 속한 사진들의 날짜 수
	 */
	public int calculatePhotoDaysCount() {
		SimpleDateFormat dateSdf = new SimpleDateFormat("yyyyMMdd");
		Set<String> dateSet = new HashSet<String>();
		
		for(ImageEntity image : getImages()) {
			String dateStr = dateSdf.format(image.getDateTime());
			dateSet.add(dateStr);
		}
		
		return dateSet.size();
	}
	
	/**
	 * 클러스터의 기간 날짜 수. (마지막시각-처음시각)을 날짜 수로 반환한다.
	 * @return 클러스터의 기간 날짜 수
	 */
	public long calculateDurationDaysCount() {
		// diffDays는 날짜 차이를 계산하기 때문에, duration을 계산하기 위해서 시작 날짜를 포함해야 하므로 1을 더해줘야 한다.
		return EfUtility.calculateDiffDays(getFirstTime(), getLastTime()) + 1;
	}
	
	/**
	 * 이 클러스터의 포인트들의 중심점
	 * @return 이 클러스터의 포인트들의 중심점, 중심점을 구할 수 없다면 null.
	 */
	public Point getCenterPoint() {
		double sumX = 0.0;
		double sumY = 0.0;
		int count = 0;
		
		for(ImageEntity image : getImages()) {
			if(image.getPoint() != null) {
				sumX += image.getPoint().getX();
				sumY += image.getPoint().getY();
				count++;
			}
		}
		
		if(count > 0) {
			Point center = new Point(sumX/count, sumY/count);
			return center;
		}
		else {
			return null;
		}
	}
	
	@Override
	public int compareTo(Cluster o) {
		return getFirstTime().compareTo(o.getFirstTime());
	}
}
