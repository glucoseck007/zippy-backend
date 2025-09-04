package com.smartlab.zippy.service.trip;

import com.smartlab.zippy.model.dto.web.response.trip.TripResponse;
import com.smartlab.zippy.model.entity.Trip;
import com.smartlab.zippy.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class TripService {

    private final TripRepository tripRepository;

    public TripService(TripRepository tripRepository) {
        this.tripRepository = tripRepository;
    }

    @Transactional(readOnly = true)
    public TripResponse getTripByOrderCode(String orderCode) {
        Trip trip = tripRepository.findTripByOrderCode(orderCode).get();

        return TripResponse.builder()
                .robotCode(trip.getRobot().getCode())
                .tripCode(trip.getTripCode())
                .startPoint(trip.getStartPoint())
                .endPoint(trip.getEndPoint())
                .startTime(trip.getStartTime())
                .endTime(trip.getEndTime())
                .status(trip.getStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public TripResponse getTripByCode(String tripCode) {
        Trip trip = tripRepository.findByTripCode(tripCode).get();

        return TripResponse.builder()
                .robotCode(trip.getRobot().getCode())
                .tripCode(trip.getTripCode())
                .startPoint(trip.getStartPoint())
                .endPoint(trip.getEndPoint())
                .startTime(trip.getStartTime())
                .endTime(trip.getEndTime())
                .status(trip.getStatus())
                .build();
    }

}
