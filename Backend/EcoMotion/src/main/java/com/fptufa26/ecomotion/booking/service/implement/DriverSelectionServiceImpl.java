package com.fptufa26.ecomotion.booking.service.implement;

import com.fptufa26.ecomotion.booking.config.OptimizationProperties;
import com.fptufa26.ecomotion.booking.dto.response.DriverCandidateResponse;
import com.fptufa26.ecomotion.booking.service.DriverSelectionService;
import com.google.ortools.Loader;
import com.google.ortools.sat.BoolVar;
import com.google.ortools.sat.CpModel;
import com.google.ortools.sat.CpSolver;
import com.google.ortools.sat.CpSolverStatus;
import com.google.ortools.sat.LinearExpr;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DriverSelectionServiceImpl implements DriverSelectionService {
    private static final Logger log = LoggerFactory.getLogger(DriverSelectionServiceImpl.class);
    private final OptimizationProperties properties;

    @Override
    public long penalty(DriverCandidateResponse c) {
        int eta = c.etaMinutes() == null ? properties.getUnknownEtaMinutes() : c.etaMinutes();
        long workload = c.activeTripsCount() == null ? 0 : c.activeTripsCount();
        double rating = c.ratingAvg() == null ? 5 : c.ratingAvg().doubleValue();
        return (long) eta * properties.getWeightEta() + workload * properties.getWeightWorkload()
                + Math.round((5 - rating) * 20) * properties.getWeightRating();
    }

    @Override
    public Selection select(List<DriverCandidateResponse> candidates) {
        List<DriverCandidateResponse> eligible = candidates.stream().filter(DriverCandidateResponse::eligible)
                .sorted(Comparator.comparingLong(this::penalty).thenComparing(DriverCandidateResponse::driverId)).toList();
        if (eligible.isEmpty()) return new Selection("INFEASIBLE", null);
        try {
            Loader.loadNativeLibraries();
            CpModel model = new CpModel();
            BoolVar[] variables = new BoolVar[eligible.size()];
            long[] penalties = new long[eligible.size()];
            for (int i = 0; i < eligible.size(); i++) {
                variables[i] = model.newBoolVar("driver_" + eligible.get(i).driverId());
                penalties[i] = penalty(eligible.get(i));
            }
            model.addExactlyOne(variables);
            model.minimize(LinearExpr.weightedSum(variables, penalties));
            CpSolver solver = new CpSolver();
            solver.getParameters().setMaxTimeInSeconds(properties.getMaxSolverTimeSeconds());
            CpSolverStatus status = solver.solve(model);
            if (status == CpSolverStatus.OPTIMAL || status == CpSolverStatus.FEASIBLE) {
                for (int i = 0; i < variables.length; i++) {
                    if (solver.booleanValue(variables[i])) return new Selection(status.name(), eligible.get(i));
                }
            }
            log.error("CP-SAT did not produce a solution: {}", status);
            return new Selection("FALLBACK_" + status.name(), eligible.getFirst());
        } catch (LinkageError | RuntimeException exception) {
            log.error("CP-SAT native execution failed; using validated candidate ranking", exception);
            return new Selection("FALLBACK_ERROR", eligible.getFirst());
        }
    }
}
