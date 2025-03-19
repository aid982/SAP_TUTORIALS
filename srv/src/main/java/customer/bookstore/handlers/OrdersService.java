package customer.bookstore.handlers;

import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CqnService;

import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.persistence.PersistenceService;

import cds.gen.booksservice.Books;
import cds.gen.booksservice.Books_;

import cds.gen.ordersservice.OrderItems_;
import cds.gen.ordersservice.Orders;
import cds.gen.ordersservice.OrderItems;

import cds.gen.ordersservice.OrdersService_;
import cds.gen.ordersservice.Orders_;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ServiceName(OrdersService_.CDS_NAME)
public class OrdersService implements EventHandler {
    @Autowired
    PersistenceService db;

    @Before(event = CqnService.EVENT_CREATE, entity = OrderItems_.CDS_NAME)
    public void validateBooks_DecreaseStocks(List<OrderItems> items) {
        for (OrderItems item : items) {
            String bookId = item.getBookId();
            Integer amount = item.getAmount();
            CqnSelect sel = Select.from(Books_.class).columns(b -> b.stock()).where(b -> b.ID().eq(bookId));
            Books book = db.run(sel).first(Books.class)
                    .orElseThrow(() -> new ServiceException(ErrorStatuses.NOT_FOUND, "Book does not exist"));
            Integer stock = book.getStock();
            if (stock < amount) {
                throw new ServiceException(ErrorStatuses.NOT_FOUND, "Not enough books on stock");
            }
            book.setStock(stock - amount);
            CqnUpdate update = Update.entity(Books_.class).data(book).where(b -> b.ID().eq(bookId));
            db.run(update);

        }
    }

    @Before(event = CqnService.EVENT_CREATE, entity = Orders_.CDS_NAME)
    public void validateBooks_DecreaseStocksViaOrders(List<Orders> orders) {
        for (Orders order : orders) {
            if (order.getItems() != null) {
                validateBooks_DecreaseStocks(order.getItems());
            }

        }
    }

    @After(event = { CqnService.EVENT_READ, CqnService.EVENT_CREATE }, entity = OrderItems_.CDS_NAME)
    public void calculateNetAmount(List<OrderItems> orderItems) {
        for (OrderItems item : orderItems) {
            String bookId = item.getBookId();

            CqnSelect sel = Select.from(Books_.class).where(el -> el.ID().eq(bookId));
            Books book = db.run(sel).single(Books.class);
            item.setNetAmount(book.getPrice().multiply(new BigDecimal(item.getAmount())));

        }
    }

    @After(event = { CqnService.EVENT_READ, CqnService.EVENT_CREATE }, entity = Orders_.CDS_NAME)
    public void calculateTotal(List<Orders> orders) {
        System.err.println(orders);
        for (Orders order : orders) {
            // calculate net amount for expanded items
            if (order.getItems() != null) {
                calculateNetAmount(order.getItems());
            }

            // get all items of the order
            CqnSelect selItems = Select.from(OrderItems_.class).where(i -> i.parent().ID().eq(order.getId()));
            List<OrderItems> allItems = db.run(selItems).listOf(OrderItems.class);

            // calculate net amount of all items
            calculateNetAmount(allItems);

            // calculate and set the orders total
            BigDecimal total = new BigDecimal(0);
            for (OrderItems item : allItems) {
                total = total.add(item.getNetAmount());
            }
            order.setTotal(total);
        }
    }

}
