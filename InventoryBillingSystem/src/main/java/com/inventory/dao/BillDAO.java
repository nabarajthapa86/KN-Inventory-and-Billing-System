package com.inventory.dao;

import com.inventory.model.Bill;
import com.inventory.model.BillItem;
import com.inventory.model.Product;
import com.inventory.util.DBConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BillDAO {

    public int saveBill(Bill bill) {
        String sql = "INSERT INTO bill (billDate, totalAmount, cashierID) VALUES (NOW(), ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setDouble(1, bill.getTotalAmount());
            ps.setInt(2, bill.getCashierID());
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) {
                int generatedID = rs.getInt(1);
                bill.setBillID(generatedID);
                for (BillItem item : bill.getItems()) saveBillItem(item, generatedID);
                return generatedID;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private void saveBillItem(BillItem item, int billID) {
        String sql = "INSERT INTO bill_items (billID, productID, quantitySold, unitPriceAtSale) VALUES (?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billID);
            ps.setInt(2, item.getProductID());
            ps.setInt(3, item.getQuantitySold());
            ps.setDouble(4, item.getUnitPriceAtSale());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates an existing bill: restores stock for old items, deletes old bill_items,
     * inserts the new items, deducts stock for new items, and updates the bill total.
     * All changes run in a single transaction — rolls back on any failure.
     *
     * @return true if the update succeeded, false otherwise
     */
    public boolean updateBill(Bill bill, List<BillItem> newItems, ProductDAO productDAO) {
        String deleteSql   = "DELETE FROM bill_items WHERE billID = ?";
        String updateTotal = "UPDATE bill SET totalAmount = ? WHERE billID = ?";

        try (Connection conn = DBConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Restore stock for the old items
                List<BillItem> oldItems = getItemsByBillID(bill.getBillID());
                for (BillItem old : oldItems) {
                    Product p = productDAO.getByID(old.getProductID());
                    if (p != null)
                        productDAO.updateStock(p.getProductID(), p.getStockQty() + old.getQuantitySold());
                }

                // 2. Delete old bill_items rows
                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setInt(1, bill.getBillID());
                    ps.executeUpdate();
                }

                // 3. Insert new bill_items and deduct stock
                String insertSql = "INSERT INTO bill_items (billID, productID, quantitySold, unitPriceAtSale) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    for (BillItem item : newItems) {
                        ps.setInt(1, bill.getBillID());
                        ps.setInt(2, item.getProductID());
                        ps.setInt(3, item.getQuantitySold());
                        ps.setDouble(4, item.getUnitPriceAtSale());
                        ps.addBatch();

                        Product p = productDAO.getByID(item.getProductID());
                        if (p != null)
                            productDAO.updateStock(p.getProductID(), p.getStockQty() - item.getQuantitySold());
                    }
                    ps.executeBatch();
                }

                // 4. Recalculate and update the bill total
                double newTotal = newItems.stream().mapToDouble(BillItem::getSubtotal).sum();
                try (PreparedStatement ps = conn.prepareStatement(updateTotal)) {
                    ps.setDouble(1, newTotal);
                    ps.setInt(2, bill.getBillID());
                    ps.executeUpdate();
                }

                bill.setItems(newItems);
                bill.setTotalAmount(newTotal);
                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                e.printStackTrace();
                return false;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Bill> getBillsByCashier(int cashierID) {
        List<Bill> bills = new ArrayList<>();
        String sql = "SELECT * FROM bill WHERE cashierID = ? ORDER BY billDate DESC";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cashierID);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Bill b = new Bill(rs.getInt("billID"), rs.getInt("cashierID"));
                    b.setBillDate(rs.getTimestamp("billDate"));
                    b.setTotalAmount(rs.getDouble("totalAmount"));
                    bills.add(b);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        for (Bill b : bills) b.setItems(getItemsByBillID(b.getBillID()));
        return bills;
    }

    public Bill getBillByID(int billID) {
        String sql = "SELECT * FROM bill WHERE billID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billID);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                Bill b = new Bill(rs.getInt("billID"), rs.getInt("cashierID"));
                b.setBillDate(rs.getTimestamp("billDate"));
                b.setTotalAmount(rs.getDouble("totalAmount"));
                b.setItems(getItemsByBillID(billID));
                return b;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<BillItem> getItemsByBillID(int billID) {
        List<BillItem> items = new ArrayList<>();
        String sql = "SELECT bi.*, p.name FROM bill_items bi JOIN product p ON bi.productID = p.productID WHERE bi.billID = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, billID);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new BillItem(rs.getInt("billItemID"), billID,
                        rs.getInt("productID"), rs.getString("name"),
                        rs.getInt("quantitySold"), rs.getDouble("unitPriceAtSale")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }
}