package com.jackleg.EventFinding;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Date;
import java.util.Calendar;

import org.apache.commons.lang3.ArrayUtils;

public class EfUtility {
	/**
	 * 데이터 리스트의 quantile 값을 저장하기 위한 클래스.
	 */
	public static class Quantiles {
		public double min;
		public double max;
		public double q1;
		public double q2;
		public double q3;

		public String toString() {
			StringBuilder sb = new StringBuilder();

			sb.append("min: ").append(min)
				.append(", q1: ").append(q1)
				.append(", q2: ").append(q2)
				.append(", q3: ").append(q3)
				.append(", max: ").append(max);

			return sb.toString();
		}
	}
	
	/**
	 * 리스트에서 어떤 데이터 값과 인덱스를 함께 전달할 때 사용
	 */
	public static class DataIndex {
		public double index;
		public double value;
	}
	
	public static final int DAY1_IN_MILLISECONDS = 24 * 60 * 60 * 1000;
	public static final int DAY7_IN_MILLISECONDS = 7 * DAY1_IN_MILLISECONDS;
	public static final int DAY1_IN_SECONDS = 24 * 60 * 60;
	public static final int HOUR1_IN_SECONDS = 60 * 60;
	public static final int MIN30_IN_SECONDS = 30 * 60;
	public static final int MIN10_IN_SECONDS = 10 * 60;
	
	/**
	 * point a, b 사이의 euclidean distance를 계산한다.
	 * @param a
	 * @param b
	 * @return point a, b의 euclidean distance
	 */
	public static double distance(Point a, Point b) {
		if(a == null || b == null) return Double.NaN;
		return Math.sqrt(Math.pow(a.getX()-b.getX(), 2.0) + Math.pow(a.getY()-b.getY(), 2.0));
	}
	
	/**
	 * Map<K, Integer>을 value를 기준으로 정렬한 후, List로 반환한다.
	 * @param <K> Key.
	 * @param map 정렬할 map.
	 * @param reverse 역순 정렬 여부.
	 * @return 정렬된 Map.Entry가 담긴 list.
	 */
	public static <K> List<Map.Entry<K, Integer>> sortMapByValue(Map<K, Integer> map, boolean reverse) {
		// 입력받은 Map을 List로 만든 후 value로 정렬
		List<Map.Entry<K, Integer>> list = new LinkedList<Map.Entry<K, Integer>> (map.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<K, Integer>>() {
			@Override
			public int compare(Entry<K, Integer> o1, Entry<K, Integer> o2) {
				return o1.getValue().compareTo(o2.getValue());
			}
		});
		if(reverse) Collections.reverse(list);
		
		return list;
	}
	
	/**
	 * 주어진 데이터의 중앙값을 계산한 후, index와 값을 반환.
	 * @param data 정렬된 데이터
	 * @return 중앙값의 index와 value.
	 */
	public static DataIndex calculateMedian(Double[] data) {
		int endIndex     = data.length - 1;
		DataIndex median = new DataIndex();
		
		if(endIndex % 2 == 0) {
			median.index = endIndex / 2;
			median.value = data[(int)median.index];
		}
		else {
			median.index = endIndex / 2.0; // endIndex가 홀수이므로, x.5 형태가 됨.
			int value1Index = endIndex / 2; // x.5에서 x만 남김
			int value2Index = value1Index + 1;
			
			median.value = (data[value1Index] + data[value2Index])/2.0;
		}
		
		return median;
	}
	
	/**
	 * 주어진 데이터의 quantile 값들을 계산. min, 25%, 50%, 75%, max에 위치한 값들을 반환한다. 
	 * @param data quantile값을 구하기 위한 데이터
	 * @return data의 quantile 값들
	 */
	public static Quantiles calculateQuantiles(Double[] data) {
		Arrays.sort(data);

		Quantiles result = new Quantiles();
		
		result.min = data[0];
		result.max = data[data.length - 1];
		
		DataIndex median = calculateMedian(data);
		
		// median의 index가 x.5와 같은 식으로 소수점일 수 있으므로, 아래와 같은 방식으로 구해야 한번에 구할 수 있다.
		// endIndex에 +1 해주는 것은 endIndex가 exclusive하기 때문.
		int startIndex = 0;
		int endIndex   = (int)Math.floor(median.index-0.5) + 1;
		DataIndex q1   = calculateMedian(ArrayUtils.subarray(data, startIndex, endIndex));
		
		startIndex   = (int)Math.ceil(median.index+0.5);
		endIndex     = data.length;
		DataIndex q3 = calculateMedian(ArrayUtils.subarray(data, startIndex, endIndex));
		
		result.q1 = q1.value;
		result.q2 = median.value;
		result.q3 = q3.value;
		
		return result;
	}
	
	/**
	 * 데이터 리스트에서 Upper Fence를 계산한다.
	 * Upper Fence는 IQR을 이용해 계산하며, 다음과 같다.
	 * upper fence = 3rd quantile + 1.5 * (3rd quantile - 1st quantile)
	 * @param data
	 * @return 데이터 리스트의 Upper Fence.
	 */
	public static double calculateUpperFence(Double[] data) {
		Arrays.sort(data);
		
		Quantiles quantiles = calculateQuantiles(data);
		return quantiles.q3 + 1.5 * (quantiles.q3 - quantiles.q1);
	}
	
	/**
	 * file의 line 데이터를 읽어 Author 리스트를 반환. file의 필드는 아래와 같으며, 필드별 구분자는 tab.
	 * author name, image url, datetime, longitude, latitude, formatted address, address component 1, address component 2, address component 3, country, ... (이후 필드는 사용하지 않음) 
	 * @param filename 데이터 파일 이름
	 * @return Author들의 리스트
	 * @throws IOException 
	 * @throws ParseException 
	 */
	public static ArrayList<Author> loadLineData(String filename) throws IOException, ParseException {
		HashMap<String, Author> authorMap = new HashMap<String, Author>();
		BufferedReader reader = new BufferedReader(new FileReader(filename));
		
		String line;
		int lineCount = 0;
		while((line = reader.readLine()) != null) {
			String[] tokens = line.split("\t", -1); // 빈 필드가 있을 수 있으므로 limit 값은 음수로 지정.
		
			String authorName  = tokens[0];
			String imageUrl    = tokens[1];
			String dateTimeStr = tokens[2];
			
			Double longitude = null;
			Double latitude  = null;
			String formattedAddress    = null;
			String[] addressComponents = null;
			String country             = null;
			try{
				longitude = Double.valueOf(tokens[3]);
				latitude  = Double.valueOf(tokens[4]);
			}
			catch(NumberFormatException e) { // 위치 정보가 없어 lng/lat 변경시 exception이 발생하는 경우. formattedAddress ~ country는 그대로 null로 남겨둔다.
				longitude = null;
				latitude  = null;
			}

			if(tokens.length >= 6) {
				if("".equals(tokens[5]) == false) {
					formattedAddress  = tokens[5];
					addressComponents = new String[3];
					addressComponents[0] = tokens[6];
					addressComponents[1] = tokens[7];
					addressComponents[2] = tokens[8];
				}
				if("".equals(tokens[9]) == false) country = tokens[9];
			}

			Author author = authorMap.get(authorName);
			if(author == null) {
				author = new Author(authorName);
				authorMap.put(authorName, author);
			}

			ImageEntity image = new ImageEntity(imageUrl, dateTimeStr, longitude, latitude, formattedAddress, addressComponents, country);
			author.addImage(image);

			lineCount++;
			if(lineCount % 1000 == 0) System.err.println("line loaded: " + lineCount);
		}
		reader.close();
		
		ArrayList<Author> resultList = new ArrayList<Author>();
		for(String key : authorMap.keySet()) { resultList.add(authorMap.get(key)); }
	
		System.err.println("loaded authors: " + resultList.size());
		return resultList;
	}

	/**
	 * 두 날짜의 날짜 수를 계산한다. 예를 들어, 5월 19일 3시 ~ 5월 20일 10시의 경우는 1일이 된다.
	 * @param beforeDate
	 * @param afterDate
	 * @return before와 after의 날짜 차이
	 */
	public static long calculateDiffDays(Date beforeDate, Date afterDate) {
		Calendar before = Calendar.getInstance();
		before.setTime(beforeDate);
		before.set(Calendar.HOUR_OF_DAY, 0);
		before.set(Calendar.MINUTE, 0);
		before.set(Calendar.SECOND, 0);
		before.set(Calendar.MILLISECOND, 0);

		Calendar after = Calendar.getInstance();
		after.setTime(afterDate);
		after.set(Calendar.HOUR_OF_DAY, 0);
		after.set(Calendar.MINUTE, 0);
		after.set(Calendar.SECOND, 0);
		after.set(Calendar.MILLISECOND, 0);

		long diffDays = 0;
		while(before.before(after)) {
			before.add(Calendar.DAY_OF_MONTH, 1);
			diffDays++;
		}

		return diffDays;
	}
	
	/**
	 * 두 날짜의 시간 부분을 제외한 년/월/일이 같은지 여부를 판단.
	 * @return 두 날짜의 년/월/일이 같으면 true, 그렇지 않으면 false.
	 */
	public static boolean isSameDay(Date date1, Date date2) {
		Calendar day1 = Calendar.getInstance();
		day1.setTime(date1);
		
		Calendar day2 = Calendar.getInstance();
		day2.setTime(date2);
		
		return (
				(day1.get(Calendar.YEAR)  == day2.get(Calendar.YEAR)) &&
				(day1.get(Calendar.MONTH) == day2.get(Calendar.MONTH)) &&
				(day1.get(Calendar.DAY_OF_MONTH) == day2.get(Calendar.DAY_OF_MONTH))
		);
	}
	
	/**
	 * 두 이미지의 시간 차이를 초(sec)로 계산하여 반환.
	 * @return 두 이미지의 시간차이의 초단위 절대값. 시간 차이를 구할 수 없다면 (이미지 중의 하나가 null이거나, 시간 정보가 없는 등) -1.
	 */
	public static long calculateTimeDiffInSec(ImageEntity p1, ImageEntity p2) {
		if(p1 == null || p1.getDateTime() == null) return -1;
		if(p2 == null || p2.getDateTime() == null) return -1;

		return calculateTimeDiffInSec(p1.getDateTime(), p2.getDateTime());
	}
	
	/**
	 * 시간 차이를 초(sec)로 계산하여 반환.
	 * @return 시간차이의 초단위 절대값. 시간 차이를 구할 수 없다면 -1.
	 */
	public static long calculateTimeDiffInSec(Date beforeDate, Date afterDate) {
		if(beforeDate == null) return -1;
		if(afterDate == null) return -1;

		return Math.abs((afterDate.getTime() - beforeDate.getTime())/1000L);
	}
}