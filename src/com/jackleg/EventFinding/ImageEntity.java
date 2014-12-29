package com.jackleg.EventFinding;

import java.util.Date;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.apache.commons.lang3.StringUtils;

public class ImageEntity
	implements Comparable<ImageEntity>
{
	private String url;
	private Date dateTime;
	private Point point;
	private String formattedAddress;
	private String[] addressComponents;
	private String country;
	private Author author;
	private LocalCluster localCluster;
	
	private static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
	
	/**
	 * Constructor.
	 * @param url
	 * @param dateTimeStr yyyyMMddHHmmss 형태임.
	 * @param longitude decimal format.
	 * @param latitude decimal format.
	 * @param formattedAddress
	 * @param addressComponents
	 * @param country
	 * @throws ParseException
	 */
	public ImageEntity(String url, String dateTimeStr, Double longitude, Double latitude, String formattedAddress, String[] addressComponents, String country)
		throws ParseException
	{
		this.url      = url;
		this.dateTime = sdf.parse(dateTimeStr);
		
		if(longitude != null && latitude != null) this.point = new Point(longitude, latitude);
		else                                      this.point = null;
		
		this.formattedAddress  = formattedAddress;
		this.addressComponents = addressComponents;
		this.country           = country;
		
		this.author       = null;
		this.localCluster = null;
	}
	
	/**
	 * Constructor. 위치 정보가 무효한 경우.
	 * @param url
	 * @param dateTimeStr
	 * @throws ParseException
	 */
	public ImageEntity(String url, String dateTimeStr)
		throws ParseException
	{
		this(url, dateTimeStr, null, null, null, null, null);
	}
	
	public Date getDateTime() { return this.dateTime; }
	public Author getAuthor() { return this.author; }
	public LocalCluster getLocalCluster() { return this.localCluster; }
	public Point getPoint() { return this.point; }
	public String getFormattedAddress() { return this.formattedAddress; }
	public String[] getAddressComponents() { return this.addressComponents; }
	public String getCountry() { return this.country; }
	public String getUrl() { return this.url; }
	
	public void setAuthor(Author author) { this.author = author; }
	public void setLocalCluster(LocalCluster cluster) { this.localCluster = cluster; }
	public void setFormattedAddres(String formattedAddress) { this.formattedAddress = formattedAddress; }
	public void setAddressCompoments(String[] addressComponents) {
		if(addressComponents == null) this.addressComponents = null;
		else {
			if(this.addressComponents == null) this.addressComponents = new String[3];
			System.arraycopy(addressComponents, 0, this.addressComponents, 0, 3);
		}
	}
	public void setCountry(String country) { this.country = country; }
	
	/**
	 * 이 이미지가 Local Cluster에 포함되어 있는지 여부.
	 * @return 이 이미지가 local cluster에 속해 있으면 true. 그렇지 않으면 false.
	 */
	public boolean isInLocalCluster() {
		return (this.localCluster != null);
	}

	/**
	 * 이 이미지가 hometown 클러스터에 속하는지 여부.
	 * @return 이 이미지가 hometown 클러스터에 속하면 1, 그렇지 않으면 0.
	 */
	public boolean isInHometown() {
		if(isInLocalCluster() == false) return false;
		return (localCluster.isHometown());
	}
	
	public String toString() {
		StringBuffer sb = new StringBuffer();
		
		sb.append(this.url)
			.append("\t").append(sdf.format(this.dateTime));
		
		// 이후의 데이터는 있는지 여부를 판단하면서 null이면 공란으로 출력.
		if(this.point == null) sb.append("\t\t");
		else                   sb.append("\t").append(this.point.toString());

		if(this.formattedAddress == null) sb.append("\t\t\t\t");
		else                              sb.append("\t").append(this.formattedAddress).append("\t").append(StringUtils.join(this.addressComponents, "\t"));
		
		if(this.country == null) sb.append("\t");
		else                     sb.append("\t").append(this.country);
		
		return sb.toString();
	}

	@Override
	public int compareTo(ImageEntity o) {
		return this.dateTime.compareTo(o.getDateTime());
	}
}
