package com.jackleg.EventFinding;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.apache.commons.lang3.StringUtils;

import com.jackleg.EventFinding.EfUtility.Quantiles;

public class Author {
	private String name;
	private ArrayList<ImageEntity> images;
	private ArrayList<LocalCluster> localClusters;
	private ArrayList<Integer> hometownIds;
	private HashSet<String> hometownCountries;
	private HashSet<String> hometownCities;
	private double[][] distanceMatrix;

	private ArrayList<EventCluster> eventCandidates;
	private ArrayList<EventCluster> events;
	
	private int reverseGeoApiCallCount = 0;
	
	// to do:
	// combo 방식에서 이벤트 후보들을 저장하기 위한 리스트.
	// 실제로는 Cluster 객체를 써야 하겠지만, 구조상의 문제로 우선 리스트로 구현.
	// 이후 로직이 정리되면 코드 정리시 바꿔 줘야 한다.
	//private ArrayList<ArrayList<ImageEntity>> eventClusters;
	//private ArrayList<ArrayList<ArrayList<ImageEntity>>> eventSubClusters;
	
	/**
	 * constructor.
	 * @param name
	 */
	public Author(String name) {
		this.name              = name;
		this.images            = null;
		this.localClusters     = null;
		this.hometownIds       = null;
		this.hometownCountries = null;
		this.hometownCities    = null;
		this.distanceMatrix    = null;
		this.eventCandidates   = null;
		this.events            = null;
	}
	
	public void setReverseGeoApiCallCount(int count) { reverseGeoApiCallCount = count; }
	
	public ArrayList<Integer> getHometownIds() { return hometownIds; }
	public ArrayList<LocalCluster> getLocalClusters() { return localClusters; }
	public int getReverseGeoApiCallCount() { return reverseGeoApiCallCount; }
	public String getName() { return name; }
	public ArrayList<ImageEntity> getImages() { return images; }
	public ImageEntity getImage(int index) {
		if(images == null) return null;
		else               return images.get(index);
	}
	
	/**
	 * 이 사용자에게 이미지를 추가.
	 * @param image 추가할 이미지
	 */
	public void addImage(ImageEntity image) {
		image.setAuthor(this);
		
		if(this.images == null) {
			this.images = new ArrayList<ImageEntity> ();
			this.images.add(image);
		}
		else {
			// first, last image를 따로 빼서 비교하는 것은 효율성 때문임. 아래의 검색 로직만 사용해도 문제 없음.
			ImageEntity lastImage  = this.images.get(this.images.size()-1);
			ImageEntity firstImage = this.images.get(0);
			
			if(lastImage.getDateTime().before(image.getDateTime()))      this.images.add(image);
			else if(firstImage.getDateTime().after(image.getDateTime())) this.images.add(0, image);
			else {
				// 뒤에서부터 image보다 dateTime이 작은 최초의 index를 찾는다. (== beforeindex) image는 beforeindex 다음에 추가된다.
				// 제일 뒤의 이미지는 이미 검사했으므로 넘어간다.
				
				for(int i=this.images.size()-2;i>=0;i--) {
					if(this.images.get(i).getDateTime().before(image.getDateTime())) {
						this.images.add(i+1, image);
						break;
					}
				}
			}
		}
	}
	
	/**
	 * 사진들을 기본 정렬 (시간순).
	 * @param reverse 역순으로 정렬할지 여부.
	 */
	public void sortImages(boolean reverse) {
		Collections.sort(images);
		if(reverse) Collections.reverse(images);
	}
	
	/**
	 * @return 이미지의 개수
	 */
	public int getImagesCount() {
		if(images == null) return 0;
		return images.size();
	}
	
	/**
	 * @return 위치 정보가 있는 이미지의 개수
	 */
	public int getGeoImagesCount() {
		if(images == null) return 0;
		
		int count = 0;		
		for (ImageEntity image : images) {
			if(image.getPoint() != null) count++;
		}
		
		return count;
	}

	/**
	 * 이미지들 사이의 거리를 얻기 위한 distance matrix를 구한다.
	 */
	private void makeDistanceMatrix() {
		// 2차원 배열을 초기화
		distanceMatrix = new double[images.size()][];
		for(int i=0; i<distanceMatrix.length; i++) {
			distanceMatrix[i] = new double[images.size()];
			Arrays.fill(distanceMatrix[i], 0.0);
		}
		
		for(int i=0;i<images.size();i++) {
			for(int j=i+1; j<images.size(); j++) { // [i][i]는 무조건 0.0, diagonal 한 쪽만 계산. 반대쪽은 같은 값을 준다.
				distanceMatrix[i][j] = distanceMatrix[j][i] = EfUtility.distance(images.get(i).getPoint(), images.get(j).getPoint());
			}
		}
	}
	
	/**
	 * 모든 포인트에서 K-distance를 구해 리스트로 반환한다.
	 * @param k K-distance에서 구할 K값.
	 * @return K-distance 값들이 담겨 있는 리스트. K-distance를 구할 수 없다면 null.
	 */
	private ArrayList<Double> getKDistanceList(int k) {
		if(k>=getGeoImagesCount()) return null; // k값이 현재 가지고 있는 이미지의 개수보다 크거나 같다면 k-distance를 구할 수 없음.
		
		if(distanceMatrix == null) makeDistanceMatrix();
		ArrayList<Double> distanceList = new ArrayList<Double>();
		
		for(int i=0;i<distanceMatrix.length;i++) {
			double[] distances = Arrays.copyOf(distanceMatrix[i], distanceMatrix[i].length);
			Arrays.sort(distances);
			
			// 정렬하고 나면 NaN이 뒤로 밀린다. 0, 1, 2, ..., NaN, NaN
			// 즉, K번째 값이 NaN이면 이 포인트는 K-distance를 구할 수 없는 것.
			// getGeoImagesCount() 체크 로직 때문에 이런 경우는 없는 것이 정상이나, 방어 코드.
			if(Double.isNaN(distances[k])) continue;
			distanceList.add(distances[k]);
		}
		return distanceList;
	}
	
	/**
	 * 주어진 pointIndex와의 거리가 threshold보다 작은 point의 index들 (neighbors) 만 반환한다.
	 * 즉, distanceMatrix[pointIndex][counterIndex] < threshold 인 counterIndex의 리스트.
	 * @param pointIndex 이웃을 구할 point index.
	 * @param threshold 거리 threshold.
	 * @return pointIndex의 이웃들의 index 리스트.
	 */
	private ArrayList<Integer> getNeighborsIndex(int pointIndex, double threshold) {
		if(distanceMatrix == null) makeDistanceMatrix();
		
		ArrayList<Integer> neighborsList = new ArrayList<Integer>();
		for(int counterIndex = 0; counterIndex < distanceMatrix[pointIndex].length; counterIndex++) {
			if((counterIndex != pointIndex) && (distanceMatrix[pointIndex][counterIndex] < threshold))
				neighborsList.add(counterIndex);
		}
		
		return neighborsList;
	}

	/**
	 * DBSCAN 기반으로 클러스터링 후 hometown을 찾는다. distanceThreshold는 내부적으로 distance 통계치를 이용한다. 
	 * @param densityThreshold DBSCAN에서 사용할 밀도 threshold. 클러스터가 가져야 할 최소의 원소 개수.
	 * @return 생성된 클러스터의 리스트.
	 */
	public ArrayList<LocalCluster> doLocalClustering(double densityThreshold) {
		ArrayList<Double> dl = getKDistanceList((int)densityThreshold);
		if(dl == null) return null;
		
		Quantiles quantiles = EfUtility.calculateQuantiles(dl.toArray(new Double[0]));
		// to do.
		// doDBSCANClustering 내부에서 localClusters에 직접 셋틍하고 있는데,
		// 클러스터링과 멤버 세팅을 분리할 필요가 있을 수 있음. 고려해 볼 것.
		int clustersCount   = doDBSCANClustering(quantiles.q3, densityThreshold);
		
		if(clustersCount == 0) return null;
		else {
			findHometown();
			return localClusters;
		}
	}
	
	/**
	 * DBSCAN 기반의 클러스터링. 
	 * @param distanceThreshold DBSCAN에서 사용할 거리 threshold.
	 * @param densityThreshold DBSCAN에서 사용할 밀도 threshold.
	 * @return 생성된 클러스터 개수.
	 */
	public int doDBSCANClustering(double distanceThreshold, double densityThreshold) {
		return doPDBSCANClustering(distanceThreshold, densityThreshold, 0.0);
	}

	/**
	 * P-DBSCAN 기반의 클러스터링. addt 값이 0.0이면 DBSCAN과 동일하다. 
	 * @param distanceThreshold P-DBSCAN에서 사용할 거리 threshold.
	 * @param densityThreshold P-DBSCAN에서 사용할 밀도 threshold.
	 * @param addt P-DBSCAN에서 사용할 addaptive threshold.
	 * @return 생성된 클러스터 개수.
	 */
	public int doPDBSCANClustering(double distanceThreshold, double densityThreshold, double addt) {
		if(distanceMatrix == null) makeDistanceMatrix();
		
		localClusters = new ArrayList<LocalCluster>();
		int cid  = 1;
		
		for(int currentIndex=0;currentIndex<getImagesCount();currentIndex++) {
			if(images.get(currentIndex).isInLocalCluster()) continue;
			
			ArrayList<Integer> neighborsIndex = getNeighborsIndex(currentIndex, distanceThreshold);
			if(neighborsIndex.size() < densityThreshold) continue;
			
			// 충분한 개수의 이웃이 있다면, 새로운 클러스터의 시작
			LocalCluster cluster = new LocalCluster(cid, this);
			cluster.addImage(images.get(currentIndex));
			double currentDensity = neighborsIndex.size();
			
			while(neighborsIndex.size() != 0) {
				int nIndex = neighborsIndex.remove(0);
				if(images.get(nIndex).isInLocalCluster()) continue;
				cluster.addImage(images.get(nIndex));
				
				ArrayList<Integer> nIndexNeighbors = getNeighborsIndex(nIndex, distanceThreshold);
				if(nIndexNeighbors.size() >= densityThreshold) {
					// P-DBSCAN의 addt setting이 설정된 경우, 밀도 체크.
					if(addt > 0.0 && nIndexNeighbors.size()/currentDensity < addt) continue;
					currentDensity = nIndexNeighbors.size();
					
					// 이미 neighbors에 들어가 있거나, 클러스터에 속한 인덱스는 제외하고 추가한다.
					for (Integer nIndexNeighbor : nIndexNeighbors) {
						if(neighborsIndex.contains(nIndexNeighbor)) continue;
						if(images.get(nIndexNeighbor).isInLocalCluster()) continue;

						neighborsIndex.add(nIndexNeighbor);						
					}
				}
			}
			
			cluster.sortImages();
			localClusters.add(cluster);
			
			cid++;
		}
		
		return localClusters.size();
	}
	
	/**
	 * author의 내용을 출력한다. 출력 필드의 순서는 다음과 같으며, 필드 구분자는 tab.
	 * author name, image(url, datetime, longitude, latitude, formatted address, address component 1~3, country), hometown flag, local cluster id
	 * @param filename 출력할 파일 이름. "stdout"을 주면 System.out을 사용한다.
	 * @param append 출력할 파일에 추가할지 여부.
	 * @throws IOException
	 */
	public void printAuthorLine(String filename, boolean append) throws IOException {
		BufferedWriter writer = null;
		StringBuffer sb = new StringBuffer();
		
		if("stdout".equals(filename)) writer = new BufferedWriter(new OutputStreamWriter(System.out));
		else                          writer = new BufferedWriter(new FileWriter(filename, append));
		
		for(ImageEntity image : images) {
			sb.setLength(0); // string buffer 초기화
			sb.append(name)
				.append("\t").append(image.toString());
			
			if(image.isInLocalCluster()) {
				LocalCluster cluster = image.getLocalCluster();
			
				if(image.isInHometown()) sb.append("\t1");
				else                     sb.append("\t0");
				
				sb.append("\t").append(cluster.getId());
			}
			
			sb.append("\n");
			writer.write(sb.toString());
		}
		
		writer.close();
	}
	
	/**
	 * 사용자의 hometown(usual places)를 예측한다.
	 * photo day(사진을 찍은 날짜 수)의 통계를 이용해 hometown을 예측하고,
	 * 이 방법이 실패하면 photo day가 가장 많은 클러스터와, 그 클러스터와 가까운 클러스터 (<10km)를 hometown으로 지정한다.
	 * @return hometown으로 지정된 클러스터 개수
	 */
	public int findHometown() {
		findHometownByQuantile();
		if(hometownIds == null) findHometownByMax();
		
		hometownCountries = getHometownCountries();
		hometownCities    = getHometownFirstAcs();
		
		return hometownIds.size();
	}
	
	/**
	 * 통계치로 hometown 구하기
	 */
	private void findHometownByQuantile() {
		if(localClusters == null || localClusters.size() <= 4) return;
		
		ArrayList<Double> photoDays = new ArrayList<Double>();
		for(LocalCluster cluster : localClusters) photoDays.add((double)cluster.calculatePhotoDaysCount());
		double upperFence = EfUtility.calculateUpperFence(photoDays.toArray(new Double[0]));
		
		ArrayList<Integer> resultList = new ArrayList<Integer>();
		for(LocalCluster cluster : localClusters) {
			if(cluster.calculatePhotoDaysCount() > upperFence) {
				cluster.setIsHometown(true);
				resultList.add(cluster.getId());
			}
		}
		
		if(resultList.size() > 0) hometownIds = resultList;
	}
	
	/**
	 * photo day가 가장 많은 위치를 hometown으로 지정한다.
	 */
	private void findHometownByMax() {
		int maxPhotoDay                 = Integer.MIN_VALUE;
		LocalCluster maxPhotoDayCluster = null;
		Point maxPhotoDayClusterCenter  = null;
	
		// to do.
		// photoday가 같은 클러스터가 많으면? 현재는 undefined이지만 고민해봐야 함.
		for(LocalCluster cluster : localClusters) {
			if(maxPhotoDay <= cluster.calculatePhotoDaysCount()) {
				maxPhotoDay        = cluster.calculatePhotoDaysCount();
				maxPhotoDayCluster = cluster;
			}
		}
		
		ArrayList<Integer> resultList = new ArrayList<Integer>();
		resultList.add(maxPhotoDayCluster.getId());
		maxPhotoDayCluster.setIsHometown(true);
		maxPhotoDayClusterCenter = maxPhotoDayCluster.getCenterPoint();
		
		for(LocalCluster cluster : localClusters) {
			if(cluster.getId() == maxPhotoDayCluster.getId()) continue; // 자기 자신은 skip.
			
			// 0.1은 약 10km. 이는 경험적인 수치이므로 수정 가능.
			if(EfUtility.distance(cluster.getCenterPoint(), maxPhotoDayClusterCenter) < 0.1) {
				resultList.add(cluster.getId());
				cluster.setIsHometown(true);
			}
		}
		
		hometownIds = resultList;
	}
	
	/**
	 * 클러스터가 어떤 hometown과 가까운 클러스터인지 여부를 확인.
	 * @param cluster
	 * @return cluster가 hometown과 가까이 있으면 true, 아니면 false.
	 */
	public boolean isCloseToHometown(LocalCluster cluster) {
		if(cluster.isHometown()) return true;
		
		Point clusterCenter = cluster.getCenterPoint();
		for(LocalCluster candCluster : localClusters) {
			// 두 클러스터의 center가 0.1 (10km) 이내인 것만 사용하는 것은 경험적인 수치임.
			if(candCluster.isHometown() && EfUtility.distance(candCluster.getCenterPoint(), clusterCenter) < 0.1)
				return true;
		}
		
		return false;
	}
	
	/**
	 * hometown 클러스터들의 나라 정보를 반환한다.
	 * @return hometown 클러스터들의 나라 정보. 없으면 null.
	 */
	private HashSet<String> getHometownCountries() {
		if(hometownIds == null || hometownIds.size() == 0) return null;
		
		HashSet<String> resultSet = new HashSet<String>();
		for(LocalCluster cluster : localClusters) {
			if(cluster.isHometown()) {
				for(ImageEntity image : cluster.getImages()) {
					if(image.getCountry() != null) resultSet.add(image.getCountry());
				}
			}
		}
		
		if (resultSet.size() == 0) return null;
		return resultSet;
	}

	/**
	 * hometown 클러스터들 1단계 address components를 반환한다.
	 * @return hometown 클러스터들의 첫번째 address component의 set. 없으면 null.
	 */
	private HashSet<String> getHometownFirstAcs() {
		if(hometownIds == null || hometownIds.size() == 0) return null;
		
		HashSet<String> resultSet = new HashSet<String>();
		for(LocalCluster cluster : localClusters) {
			if(cluster.isHometown()) {
				for(ImageEntity image : cluster.getImages()) {
					String[] acArray = image.getAddressComponents();
					if(acArray != null && acArray[0] != null) resultSet.add(acArray[0]);
				}
			}
		}
		
		if(resultSet.size() == 0) return null;
		return resultSet;
	}
	
	/**
	 * 사진들의 위치 정보를 전파.
	 * 사진들을 시간순으로 정렬해 (p1, p2) 순서가 되었을 때 p1은 local cluster 정보가 있고, p2는 local cluster 정보가 없는 경우
	 * 두 이미지의 시간 차이가 30분 이내라면 p1의  주소 정보를 p2에 복사하고, p2를 p1이 속한 local cluster에 추가한다.
	 * 또한, 두 이미지의 시간 차이가 24시간 이내라면 p1의 나라 정보를 p2로 복사한다.
	 * @param reverse 사진을 시간순으로 정렬할 때, reverse값이 true이면 역순으로 정렬한다.
	 */
	public void propagateLocalInfo(boolean reverse) {
		// to do: 나라 정보를 복사하는 것 외에는 큰 의미 없음. (실제로 이벤트 detection에 영향을 주는 것은 나라 정보 복사 뿐) 의도의 확인이 필요함. 
		if(images == null || localClusters == null || images.size() <= 1) return; // 이미지가 없거나, 클러스터링이 아직 수행되지 않았거나, 이미지가 1장 뿐이라면 종료
		
		sortImages(reverse);

		// to do: 이런 로직이라면 p1, p2, p3, p4... 가 있을 때 p1에만 위치 정보가 있고, p2, p3, p4...가 모두 30분 이내 간격을 가지고 있을 때, 모두 p1과 동일한 위치 정보를 갖게 된다.
		// 이것의 부작용은 심하지 않은가? 확인이 필요함.
		// 안전하게 하려면 p1에만 위치정보가 있다면 p1과 30분 이내인 이미지로만 위치를 전파시킨다거나 하는 식.
		// 하지만 클러스터에서 대표 포인트만 역지오코딩을 호출한다고 하면... 더 적극적으로 전파할 필요도 있음.
		ImageEntity prevImage = images.get(0);
		for(int i=1;i<images.size();i++) {
			ImageEntity currImage = images.get(i);
			
			// 주소 정보 및 클러스터 추가 여부 확인
			if(prevImage.isInLocalCluster()
				&& currImage.isInLocalCluster() == false
				&& EfUtility.calculateTimeDiffInSec(prevImage, currImage) < EfUtility.MIN30_IN_SECONDS) {
				currImage.setFormattedAddres(prevImage.getFormattedAddress());
				currImage.setAddressCompoments(prevImage.getAddressComponents());
				prevImage.getLocalCluster().addImage(currImage);
			}
			
			// 나라 정보 추가 여부 확인
			if(prevImage.getCountry() != null
				&& currImage.getCountry() == null
				&& EfUtility.calculateTimeDiffInSec(prevImage, currImage) < EfUtility.DAY1_IN_SECONDS) {
				currImage.setCountry(prevImage.getCountry());
			}

			prevImage = currImage;
		}
		
		// reverse로 작업했다면 다시 원래대로 돌려놓는다.
		if(reverse) sortImages(false);
	}
	
	/**
	 * 이미지가 해외(hometown이 아닌 나라)에 있지 여부를 판단.
	 * @return 이미지가 국가 정보를 가지고 있고 해외에 있다면 true, 그렇지 않으면 false;
	 */
	public boolean isAbroad(ImageEntity image) {
		if(hometownCountries == null) return false;
		
		if(image.getCountry() != null && hometownCountries.contains(image.getCountry()) == false) return true;
		return false;
	}
	
	/**
	 * 이미지가 hometown city 외부에 있는지 여부 
	 * @return 이미지가 hometown city와 다른 city에 있다면 true, 그렇지 않으면 false.
	 */
	public boolean isOutOfHometownCity(ImageEntity image) {
		if(hometownCities == null) return false;
		
		String[] imageAcs = image.getAddressComponents();
		if(imageAcs != null && hometownCities.contains(imageAcs[0]) == false) return true;
		return false;
	}
	
	/**
	 * 이벤트 후보를 만들 대, prevImage와 currImage 사이를 끊어야 하는지 여부를 판단.
	 * 1. 두 이미지가 모두 해외(hometown이 아닌 나라)이고, 시간 간격이 36시간 이내라면 분리하지 않음.
	 * 2. 둘 중 한개 이상이 홈타운 사진이고 두 사진이 3시간 간격 이상이면 분할.
	 * 3. 두 이미지가 모두 hometown 이미지가 아닐 때,
	 * 3-1. 두 이미지가 모두 hometown city와 다른 city에 있을 때,
	 * 3-1-1. 두 이미지가 36시간 이내라면 분리하지 않음.
	 * 3-1-2. 두 이미지가 36시간 이상이라면 분리.
	 * 3-2. 두 이미지 중의 하나라도 hometown city에 있을 때,
	 * 3-2-1. 두 이미지가 12시간 이내라면 분리하지 않음.
	 * 3-2-2. 두 이미지가 12시간 이상이라면 분리.
	 * 4. 두 이미지가 12시간 이상이라면 분리.
	 * 5. 분리하지 않음.
	 * @return 분리해야 하면 true, 그렇지 않으면 false.
	 */
	private boolean splitHere(ImageEntity prevImage, ImageEntity currImage) {
		if(prevImage == null || currImage == null) return false;
		
		long timeDiffInSec = EfUtility.calculateTimeDiffInSec(prevImage, currImage);
		
		if(isAbroad(prevImage) && isAbroad(currImage) && timeDiffInSec < 36 * EfUtility.HOUR1_IN_SECONDS) {
			return false;
		}
		
		if( (prevImage.isInHometown() || currImage.isInHometown())
			&& timeDiffInSec >= 3 * EfUtility.HOUR1_IN_SECONDS) {
			return true;
		}

		if(prevImage.isInHometown() == false && currImage.isInHometown() == false) {
			//if(isOutOfHometownCity(prevImage) && isOutOfHometownCity(currImage)) {
			LocalCluster prevCluster = prevImage.getLocalCluster();
			LocalCluster currCluster = currImage.getLocalCluster();
			
			if( (prevCluster != null && currCluster != null) &&
					prevImage.getLocalCluster().getId() == currImage.getLocalCluster().getId()) {
				if(timeDiffInSec < 36 * EfUtility.HOUR1_IN_SECONDS) return false;
				else return true;
			}
			else {
				if(timeDiffInSec < 12 * EfUtility.HOUR1_IN_SECONDS) return false;
				else return true;
			}
		}
		
		if(timeDiffInSec >= 12 * EfUtility.HOUR1_IN_SECONDS) {
			return true;
		}
		
		return false;
	}
	
	/**
	 * 사진 리스트들로부터 이벤트 후보 리스트를 생성한다. 여기에서 생성된 후보(이미지의 묶음) 중에서 실제 이벤트를 찾는다.
	 */
	private void makeEventCandidates() {
		// to do: 현재는 실험을 위해 candidates를 모두 멤버로 저장하고 있으나 실제로는 이벤트만 있으면 되므로 이럴 필요는 없음.
		// 개선 가능한지 여부를 고려해 볼 것.
		if(images == null || images.size() <= 1) return;
		
		eventCandidates = new ArrayList<EventCluster>();
		int cid = 10000;
		EventCluster eventCandidate = new EventCluster(cid, this);
		eventCandidates.add(eventCandidate);

		ImageEntity prevImage = null;
		for (ImageEntity currImage : images) {
			if(splitHere(prevImage, currImage)) {
				// 현재 prevImage까지 들어가 있음.
				// 새로운 클러스터를 만들고 리스트에 추가한다.
				// to do. 클러스터 추가할 때 id 생성하는것도 wrapping할 필요가 있을 듯...
				cid++;
				eventCandidate = new EventCluster(cid, this);
				eventCandidates.add(eventCandidate);
			}
			eventCandidate.addImage(currImage);
			prevImage = currImage;
		}
	}

	/**
	 * 사진 리스트들로부터 이벤트를 찾는다.
	 * @return 발견한 이벤트들
	 */
	public ArrayList<EventCluster> findEvents() {
		makeEventCandidates();
		
		for(EventCluster eventCandidate : eventCandidates) {
			if(eventCandidate.isEvent()) {
				if(events == null) events = new ArrayList<EventCluster>();
				events.add(eventCandidate);
			}
		}
		
		return events;
	}
	
	public ArrayList<EventCluster> findTravelEvents() {
		eventCandidates = new ArrayList<EventCluster>();
		
		sortImages(false);
		
		int id = 0;
		EventCluster event = new EventCluster(id, this);
		
		for(int index=0;index<getImagesCount();index++) {
			ImageEntity currImage = getImage(index);
			
			if(currImage.isInHometown()) { // 현재 이미지가 hometown인 경우는 클러스터의 마지막인지 여부를 판단
				// 1. 첫 이미지라면 skip.
				if(index == 0) continue;
				
				ImageEntity prevImage = getImage(index-1);
				// 2. 바로 앞의 이미지가 hometown이 아니고, 같은 날짜라면 클러스터에 계속 추가됨
				if(prevImage.isInHometown() == false && EfUtility.isSameDay(prevImage.getDateTime(), currImage.getDateTime())) event.addImage(currImage);
				// 3. 바로 앞의 이미지가 hometown이며, 클러스터에 속해 있고, 나와 10분 이내라면 추가.
				else if(prevImage.isInHometown() && event.contains(prevImage) && EfUtility.calculateTimeDiffInSec(prevImage, currImage)< EfUtility.MIN10_IN_SECONDS) event.addImage(currImage);
				// 그 외의 경우는 클러스터에 속하지 않는 경우.
				// 진행되고 있던 클러스터가 있다면 종료하고 새로운 클러스터를 만든다.
				else {
					if(event.getImagesCount() > 0) {
						eventCandidates.add(event);
						id++;
						event = new EventCluster(id, this);
					}
				}
			}
			else { // 현재 이미지가 hometown이 아닌 경우
				// 현재 이미지 앞의 이미지가 hometown이고, 같은 날 찍은 사진들이라면 클러스터에 추가한다.
				// hometown들의 사진은 10분 이내에 찍혀 있어야 한다.
				ImageEntity homeStartImage = null;
				for(int beforeIndex = index-1; beforeIndex >= 0; beforeIndex--) {
					ImageEntity image = getImage(beforeIndex);
					
					if(image.isInHometown() && EfUtility.isSameDay(image.getDateTime(), currImage.getDateTime())) {
						if(homeStartImage == null) homeStartImage = image;
						
						if(EfUtility.calculateTimeDiffInSec(homeStartImage, image) < EfUtility.MIN10_IN_SECONDS) event.addImage(image);
						else break;
					}
					else break;
				}
				
				event.addImage(currImage);
			}
		}
		
		// 마지막까지 사진을 클러스터에 추가만 하고 처리되지 못한 클러스터가 있는 경우
		if(event.getImagesCount() > 0) eventCandidates.add(event);

		return eventCandidates;
	}
	
	/**
	 * 이벤트 후보 리스트를 파일로 출력. (실험용)
	 * @param path 결과를 출력할 디렉토리명.
	 * @throws IOException 
	 */
	public void printEventCandidates(String path, String filename) throws IOException {
		String overviewFilename = path + "/" + filename;
		BufferedWriter overviewWriter = new BufferedWriter(new FileWriter(overviewFilename));
		
		overviewWriter.write("<meta charset=\"utf-8\"/>\n");
		overviewWriter.write("<h1>"+name+"님의 의미있는 순간들</h1>");
		
		overviewWriter.write("<h3>사진: " + getImagesCount() +"장 | 위치 정보 있는 사진: " + getGeoImagesCount() + "장 | 역지오코딩 API 호출 회수: " + getReverseGeoApiCallCount() +"회</h3>");
		if(hometownCountries != null) overviewWriter.write("<h3>hometown country</h3>" + StringUtils.join(hometownCountries.toArray(new String[0]), ", ") + "<br/>");
		if(hometownCities != null) overviewWriter.write("<h3>hometown city</h3>" + StringUtils.join(hometownCities.toArray(new String[0]), ", ") + "<br/>");
		
		for(int i=eventCandidates.size()-1;i>=0;i--) {
			EventCluster eventCandidate = eventCandidates.get(i);
			
			String fontColor    = eventCandidate.isEvent() ? "#5555" : "#D3D3D3";
			
			overviewWriter.write("<h2><font color=" + fontColor +">[" + eventCandidate.getId() +"] " + eventCandidate.getRange() + " " + eventCandidate.getRepLocation() + " 찍은 사진 " + eventCandidate.getImagesCount() + "장</font></h2>");
			
			int gap = 1;
			if(eventCandidate.getImagesCount() > 4) gap = eventCandidate.getImagesCount()/2;
			
			if(eventCandidate.isEvent()) {
				for(int j=0;j<eventCandidate.getImagesCount();j+=gap) {
					overviewWriter.write("<img src=" + eventCandidate.getImage(j).getUrl() + " height=100px></img>");
				}
			}
			
			overviewWriter.write("<a href=\"" + name + "." + eventCandidate.getId() + ".html\">자세히</a>");
			overviewWriter.write("<hr>");
			
			String subFilename = path + "/" + name + "." + eventCandidate.getId() + ".html";
			BufferedWriter subWriter = new BufferedWriter(new FileWriter(subFilename));
			subWriter.write("<meta charset=\"utf-8\"/>\n");
			
			for(EventCluster subCluster : eventCandidate.splitEventCluster()) {
				subWriter.write("<h4>" + subCluster.getRange() + " " + subCluster.getRepLocation() + " 찍은 사진 " + subCluster.getImagesCount() + " 장</h4>");
				
				for(int l=0;l<subCluster.getImagesCount();l++) {
					if(subCluster.getImage(l).isInHometown())
						subWriter.write("<img src=" + subCluster.getImage(l).getUrl() + " height=100px border=\"10\" bordercolor=\"#FEABCD\"></img>");
					else
						subWriter.write("<img src=" + subCluster.getImage(l).getUrl() + " height=100px></img>");
				}
				
				subWriter.write("<hr>");
			}
			
			subWriter.close();
		}
		
		overviewWriter.close();
	}
	
	/**
	 * 이 사용자의 포인트들의 모든 위치 정보를 삭제한다. approximate reverse-geocoding api call을 시뮬레이션하기 위함. (실험용)
	 */
	public void clearLocalInfo() {
		for(ImageEntity image : images) {
			image.setCountry(null);
			image.setFormattedAddres(null);
			image.setAddressCompoments(null);
		}
	}
}