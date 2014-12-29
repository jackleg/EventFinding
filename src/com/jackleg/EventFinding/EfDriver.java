package com.jackleg.EventFinding;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;

public class EfDriver {
	public static HashMap<Point, String> countryMap = new HashMap<Point, String>();
	public static HashMap<Point, String> acFirstMap = new HashMap<Point, String>();
	public static HashMap<Point, String> acSecondMap = new HashMap<Point, String>();
	public static String sampleGeoFile = "./sample/sample.geo.txt";
	
	public static void writeSampleGeoFile(ArrayList<Author> authors) throws IOException {
		BufferedWriter simFile = new BufferedWriter(new FileWriter(sampleGeoFile));
		
		for(Author author : authors) {
			ArrayList<LocalCluster> clusters = author.doLocalClustering(10);
			
			// 시뮬레이션을 위한 샘플 데이터 출력
			if(clusters != null) {
				for(LocalCluster cluster : clusters) {
					ImageEntity ci = cluster.getImageForApproxRG();
					if(ci.getAddressComponents() != null) {
						simFile.write(ci.getPoint().getX() + "\t" + ci.getPoint().getY() + "\t" + ci.getAddressComponents()[0] + "\t" + ci.getAddressComponents()[1] + "\t" + ci.getCountry() + "\n");
					}
				}
			}
		}
		
		simFile.close();
	}
	
	public static void loadSampleGeoFile() throws IOException {
		BufferedReader reader = new BufferedReader(new FileReader(sampleGeoFile));
		String line;
		
		while((line=reader.readLine()) != null) {
			String[] tokens = line.split("\t");
			
			double x = Double.parseDouble(tokens[0]);
			double y = Double.parseDouble(tokens[1]);
			String acFirst  = tokens[2];
			String acSecond = tokens[3];
			String country  = tokens[4];
			
			Point point = new Point(x, y);
			
			countryMap.put(point, country);
			acFirstMap.put(point, acFirst);
			acSecondMap.put(point, acSecond);
		}
		
		reader.close();
	}
	
	public static void main(String[] args) throws IOException, ParseException {
		if(args.length < 2) {
			System.err.println("usage: java TossDriver <inputfile> <outputfile>");
			System.err.println("    inputfile : input file. see. TossUtility#loadLineData");
			System.err.println("    outputdir : output directory where to put result files.");
			System.err.println("");
			System.exit(1);
		}
		
		String inputfilename  = args[0];
		String outputfilename = args[1];
	
		System.err.println("load data file: " + inputfilename);
		ArrayList<Author> authors = EfUtility.loadLineData(inputfilename);

		//writeSampleGeoFile(authors); // reverse-geocoding API call을 시뮬레이션하기 위한 geo 정보 sample file 생성
		loadSampleGeoFile(); // reverse-geocoding API call을 시뮬레이션하기 위해 미리 생성한 geo 정보 sample file을 읽는다.
		
		// index 파일 header
		BufferedWriter indexWriter = new BufferedWriter(new FileWriter(outputfilename + "/index.html"));
		indexWriter.write("<meta charset=\"utf-8\"/>\n");
		indexWriter.write("<h3>toss event 데모 </h3>");
		
		for(Author author : authors) {
			System.err.println("find event for " + author.getName());
			
			/* 위치 정보가 충분히 있는 경우의 일반적인 실행 순서 start */
//			author.doLocalClustering(10);
//			
//			// 정방향, 역방향으로 위치 정보 전파
//			author.propagateLocalInfo(false);
//			author.propagateLocalInfo(true);
//
//			author.findEvents();
//			author.printEventCandidates(outputfilename);
			/* 위치 정보가 충분히 있는 경우의 일반적인 실행 순서 end */
			
			/* 위치 정보가 없는 경우의 시뮬레이션 start */
			ArrayList<LocalCluster> localClusters = author.doLocalClustering(10);
			
			author.clearLocalInfo(); // 시뮬레이션을 위해 주소, 나라 정보를 삭제
			int reverseGeoApiCallCount = 0;
			
			if(localClusters != null) {
				for(LocalCluster localCluster : localClusters) {
					ImageEntity centerImage = localCluster.getImageForApproxRG(); // 클러스터에서 reverse-geocoding을 위해 center와 가장 가까운 이미지를 얻음
					
					String country = countryMap.get(centerImage.getPoint()); // reverse-geocoding을 대신해서 미리 만들어 둔 나라, 도시 맵을 이용.
					String acFirst = acFirstMap.get(centerImage.getPoint());
					String acSecond = acSecondMap.get(centerImage.getPoint());
					
					localCluster.setApproxLocalInfo(acFirst, acSecond, country);
					reverseGeoApiCallCount++;
				}
			}
			author.setReverseGeoApiCallCount(reverseGeoApiCallCount);
			
			// 정방향, 역방향으로 위치 정보 전파
			author.propagateLocalInfo(false);
			author.propagateLocalInfo(true);

			ArrayList<EventCluster> events = author.findEvents();
			if(events != null) {
				System.err.println("events for " + author.getName());
				for(EventCluster event : events) {
					System.err.println("[" + event.getRepLocation() + "][" + event.getRange() +"] " + event.getImagesCount() + "장");
				}
			}
			
			String authorFilename = author.getName() + ".html";
			indexWriter.write("<a href=\"" + authorFilename + "\">" + author.getName() + "</a><br/>");
			author.printEventCandidates(outputfilename, authorFilename);
			/* 위치 정보가 없는 경우의 시뮬레이션 end */
			
			// 폐기함
			/* 여행 이벤트 찾기 로직 시작 */
//			author.doLocalClustering(10);
//
//			// 정방향, 역방향으로 위치 정보 전파
//			author.propagateLocalInfo(false);
//			author.propagateLocalInfo(true);
//
//			ArrayList<EventCluster> events = author.findTravelEvents();
//			if(events != null) {
//				System.err.println("events for " + author.getName());
//				for(EventCluster event : events) {
//					System.err.println("[" + event.getRepLocation() + "][" + event.getRange() +"] " + event.getImagesCount() + "장");
//				}
//			}
//			author.printEventCandidates(outputfilename);
			/* 여행 이벤트 찾기 로직 끝 */
		}
		
		indexWriter.close();
		System.err.println("done.");
	}
}