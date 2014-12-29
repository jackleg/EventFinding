package com.jackleg.EventFinding;

/**
 * 위치 정보 기만 클러스터링 결과를 저장하기 위한 클러스터
 */
public class LocalCluster extends Cluster {
	private boolean isHometownFlag;
	
	/**
	 * constructor.
	 */
	public LocalCluster() {
		this(0, null);
	}

	/**
	 * constructor.
	 */
	public LocalCluster(int id, Author author) {
		super(id, author);

		this.isHometownFlag = false;
	}

	/**
	 * @return 이 클러스터가 hometown인지 여부.
	 */
	public boolean isHometown() { return isHometownFlag; }
	
	public void setIsHometown(boolean isHometownFlag) { this.isHometownFlag = isHometownFlag; }
	
	@Override
	/**
	 * 이미지를 이 클러스터에 추가.
	 */
	public void addImage(ImageEntity image) {
		super.addImage(image);
		image.setLocalCluster(this);
	}
	
	/**
	 * 역지오코딩 API 호출을 위해 클러스터에서 가장 중심에 가까운 사진의 포인트를 반환.
	 * 현재 이벤트 찾기 로직에서 이미지의 주소/나라 정보를 사용하고 있기 때문에, 수치적인 중심점을 찾는 getCenterPoint()만을 사용할 수는 없고,
	 * getCenterPoint에 가장 가까운 이미지의 포인트를 찾아서 반환해야 한다. 
	 * @return 이 클러스터의 중심에 가장 가까운 이미지의 좌표 정보.
	 */
	public ImageEntity getImageForApproxRG() {
		Point center            = getCenterPoint();
		double minDistance      = Double.MAX_VALUE;
		ImageEntity centerImage = null;
		
		for(ImageEntity image : images) {
			if(image.getPoint() == null) continue;
			
			double distance = EfUtility.distance(center, image.getPoint());
			if(distance < minDistance) {
				minDistance = distance;
				centerImage = image;
			}
		}
		
		return centerImage;
	}
	
	/**
	 * 임시 위치 정보를 셋팅한다.<br/>
	 * 이 함수로 전달되는 나라 정보와, 첫 번째 주소 컴포넌트는 클러스터 내의 해당 정보가 없는 모든 포인트에 지정된다. 
	 * @param acFirst 첫 번째 주소 컴포넌트 값. 한국의 경우 시/도 단위이다. (e.g. 서울특별시, 충청남도...)
	 * @param country 나라 정보
	 */
	public void setApproxLocalInfo(String acFirst, String acSecond, String country) {
		String[] acs = {acFirst, acSecond, null};
		
		for(ImageEntity image : images) {
			if(image.getCountry() == null) image.setCountry(country);
			if(image.getAddressComponents() == null) image.setAddressCompoments(acs);
		}
	}	
}