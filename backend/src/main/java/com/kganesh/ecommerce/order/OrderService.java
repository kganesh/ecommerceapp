package com.kganesh.ecommerce.order;

import com.kganesh.ecommerce.common.ApiExceptions;
import com.kganesh.ecommerce.order.OrderDtos.AcknowledgedWarning;
import com.kganesh.ecommerce.order.OrderDtos.Order;
import com.kganesh.ecommerce.order.OrderDtos.OrderLine;
import com.kganesh.ecommerce.safety.WarningService;
import com.kganesh.ecommerce.safety.WarningService.RecordedWarning;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** FR-15: order history, with the warnings the parent acknowledged for each line. */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final WarningService warnings;

    OrderService(OrderRepository orders, WarningService warnings) {
        this.orders = orders;
        this.warnings = warnings;
    }

    @Transactional(readOnly = true)
    public List<Order> list(UUID userId) {
        return build(orders.findForUser(userId, null));
    }

    @Transactional(readOnly = true)
    public Order get(UUID userId, UUID orderId) {
        List<Order> found = build(orders.findForUser(userId, orderId));
        if (found.isEmpty()) {
            throw new ApiExceptions.NotFound("Order " + orderId + " not found");
        }
        return found.getFirst();
    }

    private List<Order> build(List<OrderRepository.OrderRow> rows) {
        List<OrderRepository.LineRow> lines = orders.lines(rows.stream().map(OrderRepository.OrderRow::id).toList());
        Map<UUID, RecordedWarning> recorded = warnings.recorded(lines.stream()
                .map(OrderRepository.LineRow::acknowledgedWarningId)
                .filter(Objects::nonNull)
                .toList());
        return rows.stream()
                .map(o -> new Order(o.id(), o.status(), o.placedAt(), o.totalCents(), o.currency(), o.paymentReference(),
                        lines.stream()
                                .filter(l -> l.orderId().equals(o.id()))
                                .map(l -> new OrderLine(l.productId(), l.productName(), l.qty(), l.unitPriceCents(),
                                        l.unitPriceCents() * l.qty(), l.allergenVersion(),
                                        toAcknowledged(recorded.get(l.acknowledgedWarningId()))))
                                .toList()))
                .toList();
    }

    private static AcknowledgedWarning toAcknowledged(RecordedWarning w) {
        return w == null ? null
                : new AcknowledgedWarning(w.context(), w.unverified(), w.shownAt(), w.ackedAt(), w.matches());
    }
}
