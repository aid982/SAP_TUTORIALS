package customer.bookstore.handlers;


import com.sap.cds.ql.Select;
import com.sap.cds.ql.Update;
import com.sap.cds.ql.cqn.CqnSelect;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.services.ErrorStatuses;
import com.sap.cds.services.ServiceException;
import com.sap.cds.services.cds.CqnService;

import com.sap.cds.services.handler.EventHandler;
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
            CqnSelect sel = Select.from(Books_.class).columns(b->b.stock()).where(b->b.ID().eq(bookId));
            Books book = db.run(sel).first(Books.class).orElseThrow(()->new ServiceException(ErrorStatuses.NOT_FOUND,"Book does not exist"));
            Integer stock = book.getStock();
            if(stock<amount) {
                throw new ServiceException(ErrorStatuses.NOT_FOUND,"Not enough books on stock");
            }
            book.setStock(stock-amount);
            CqnUpdate update = Update.entity(Books_.class).data(book).where(b->b.ID().eq(bookId));
            db.run(update);
            
        }
    }

    @Before(event = CqnService.EVENT_CREATE, entity = Orders_.CDS_NAME)
    public void validateBooks_DecreaseStocksViaOrders(List<Orders> orders) {
        for (Orders order : orders) {
            if(order.getItems()!=null) {
                validateBooks_DecreaseStocks(order.getItems());
            }
            
        }
    }



}
