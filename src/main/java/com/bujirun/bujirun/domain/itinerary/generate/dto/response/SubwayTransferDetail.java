package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

public record SubwayTransferDetail(
        int takeStationId,     // 탑승역 ID
        String takeLaneName,   // 탑승 노선명
        int exStationId,       // 환승역 ID
        String exLaneName,     // 환승할 노선명
        Integer fastTrainNo,   // 빠른 환승 열차번호 (ODsay 원문 필드명: FastTrain) - 정보 없으면 null
        Integer fastTrainDoor  // 빠른 환승 시 이용할 문 번호 (ODsay 원문 필드명: FastFastDoor) - 정보 없으면 null
) {}
