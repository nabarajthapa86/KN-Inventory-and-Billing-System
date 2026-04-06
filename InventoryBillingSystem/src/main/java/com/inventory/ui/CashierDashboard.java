package com.inventory.ui;

import com.inventory.dao.BillDAO;
import com.inventory.dao.ProductDAO;
import com.inventory.model.Bill;
import com.inventory.model.BillItem;
import com.inventory.model.Cashier;
import com.inventory.model.Product;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CashierDashboard extends JFrame {

    private final Cashier cashier;
    private final ProductDAO productDAO = new ProductDAO();
    private final BillDAO billDAO = new BillDAO();

    private JComboBox<Product> cmbProduct;
    private JTextField txtQty;
    private DefaultTableModel billModel;
    private JLabel lblTotal;
    private final List<BillItem> currentItems = new ArrayList<>();
    private int tempItemID = 1;

    public CashierDashboard(Cashier cashier) {
        this.cashier = cashier;
        setTitle("Cashier Dashboard — " + cashier.getUsername());
        setSize(780, 560);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        initComponents();
    }

    private void initComponents() {
        JMenuBar menuBar = new JMenuBar();
        JMenu menuAccount = new JMenu("Account");
        JMenuItem itemLogout = new JMenuItem("Logout");
        itemLogout.addActionListener(e -> { dispose(); new LoginFrame().setVisible(true); });
        menuAccount.add(itemLogout);
        JMenu menuBills = new JMenu("My Bills");
        JMenuItem itemViewBills = new JMenuItem("View Past Bills");
        itemViewBills.addActionListener(e -> showPastBills());
        menuBills.add(itemViewBills);
        menuBar.add(menuAccount);
        menuBar.add(menuBills);
        setJMenuBar(menuBar);

        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        topPanel.setBorder(BorderFactory.createTitledBorder("Add Item"));
        cmbProduct = new JComboBox<>();
        cmbProduct.setPreferredSize(new Dimension(240, 28));
        loadProducts();
        txtQty = new JTextField("1", 5);
        JButton btnAdd = new JButton("Add to Bill");
        btnAdd.addActionListener(e -> addItemToBill());
        topPanel.add(new JLabel("Product:")); topPanel.add(cmbProduct);
        topPanel.add(new JLabel("Qty:"));     topPanel.add(txtQty);
        topPanel.add(btnAdd);
        add(topPanel, BorderLayout.NORTH);

        billModel = new DefaultTableModel(new String[]{"#", "Product", "Qty", "Unit Price", "Subtotal"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable billTable = new JTable(billModel);
        billTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(billTable), BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(8, 4));
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        JButton btnRemove = new JButton("Remove Selected");
        JButton btnClear  = new JButton("Clear Bill");
        JButton btnSave   = new JButton("Print");
        btnSave.setBackground(new Color(34, 197, 94));
        btnSave.setForeground(Color.WHITE);
        btnSave.setFocusPainted(false);

        btnRemove.addActionListener(e -> {
            int row = billTable.getSelectedRow();
            if (row >= 0) { currentItems.remove(row); refreshBillTable(); }
        });
        btnClear.addActionListener(e -> { currentItems.clear(); refreshBillTable(); });
        btnSave.addActionListener(e -> saveBill());

        actionPanel.add(btnRemove); actionPanel.add(btnClear); actionPanel.add(btnSave);
        lblTotal = new JLabel("Total: Rs. 0.00", SwingConstants.RIGHT);
        lblTotal.setFont(new Font("SansSerif", Font.BOLD, 16));
        lblTotal.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
        bottomPanel.add(actionPanel, BorderLayout.WEST);
        bottomPanel.add(lblTotal, BorderLayout.EAST);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void loadProducts() {
        cmbProduct.removeAllItems();
        for (Product p : productDAO.getAll()) {
            if (p.isAvailable()) cmbProduct.addItem(p);
        }
    }

    private void addItemToBill() {
        Product selected = (Product) cmbProduct.getSelectedItem();
        if (selected == null) { warn("No products available."); return; }
        int qty;
        try {
            qty = Integer.parseInt(txtQty.getText().trim());
            if (qty <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            warn("Enter a valid quantity (positive number)."); return;
        }
        if (qty > selected.getStockQty()) {
            warn("Not enough stock. Available: " + selected.getStockQty()); return;
        }
        for (BillItem existing : currentItems) {
            if (existing.getProductID() == selected.getProductID()) {
                int newQty = existing.getQuantitySold() + qty;
                if (newQty > selected.getStockQty()) { warn("Total qty exceeds stock."); return; }
                existing.setQuantitySold(newQty);
                refreshBillTable();
                return;
            }
        }
        currentItems.add(new BillItem(tempItemID++, 0, selected.getProductID(),
                selected.getName(), qty, selected.getPrice()));
        refreshBillTable();
    }

    private void refreshBillTable() {
        billModel.setRowCount(0);
        double total = 0;
        int i = 1;
        for (BillItem item : currentItems) {
            billModel.addRow(new Object[]{i++, item.getProductName(), item.getQuantitySold(),
                    String.format("Rs. %.2f", item.getUnitPriceAtSale()),
                    String.format("Rs. %.2f", item.getSubtotal())});
            total += item.getSubtotal();
        }
        lblTotal.setText(String.format("Total: Rs. %.2f", total));
    }

    private void saveBill() {
        if (currentItems.isEmpty()) { warn("Add at least one item."); return; }
        Bill bill = new Bill(0, cashier.getCashierID());
        for (BillItem item : currentItems) { item.setBillID(0); bill.addItem(item); }
        int savedID = billDAO.saveBill(bill);
        if (savedID < 0) { warn("Failed to save bill."); return; }
        for (BillItem item : currentItems) {
            Product p = productDAO.getByID(item.getProductID());
            if (p != null) productDAO.updateStock(p.getProductID(), p.getStockQty() - item.getQuantitySold());
        }
        showBillReceipt(bill);
        currentItems.clear();
        refreshBillTable();
        loadProducts();
    }

    private void showBillReceipt(Bill bill) {
        StringBuilder sb = new StringBuilder();
        sb.append("==============================\n");
        sb.append("     INVENTORY & BILLING\n");
        sb.append("==============================\n");
        sb.append(String.format("Bill No : %d\n", bill.getBillID()));
        sb.append(String.format("Cashier : %s\n", cashier.getUsername()));
        sb.append(String.format("Date    : %s\n", bill.getBillDate()));
        sb.append("------------------------------\n");
        sb.append(String.format("%-18s %4s %8s\n", "Product", "Qty", "Amount"));
        sb.append("------------------------------\n");
        for (BillItem item : bill.getItems())
            sb.append(String.format("%-18s %4d %8.2f\n",
                    item.getProductName(), item.getQuantitySold(), item.getSubtotal()));
        sb.append("------------------------------\n");
        sb.append(String.format("TOTAL           : Rs. %.2f\n", bill.getTotalAmount()));
        sb.append("==============================\n     Thank you!\n");

        JTextArea ta = new JTextArea(sb.toString());
        ta.setFont(new Font("Monospaced", Font.PLAIN, 13));
        ta.setEditable(false);
        JScrollPane sp = new JScrollPane(ta);
        sp.setPreferredSize(new Dimension(360, 360));
        JPanel receiptPanel = new JPanel(new BorderLayout(4, 8));
        receiptPanel.add(sp, BorderLayout.CENTER);
        JButton btnPrint = new JButton("Print");
        btnPrint.addActionListener(e -> saveBillAsImage(sb.toString(), bill.getBillID()));
        receiptPanel.add(btnPrint, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this, receiptPanel, "Bill Receipt — #" + bill.getBillID(), JOptionPane.PLAIN_MESSAGE);
    }

    private void saveBillAsImage(String receiptText, int billID) {
        Font font = new Font("Monospaced", Font.PLAIN, 16);
        String[] lines = receiptText.split("\n");
        int padding    = 20;
        int lineHeight = 22;
        int imgWidth   = 420;
        int imgHeight  = padding * 2 + lines.length * lineHeight;

        java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(imgWidth, imgHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, imgWidth, imgHeight);
        g.setColor(Color.BLACK);
        g.setFont(font);
        int y = padding + lineHeight;
        for (String line : lines) {
            g.drawString(line, padding, y);
            y += lineHeight;
        }
        g.dispose();

        // Check all common Desktop locations including OneDrive
        java.io.File desktop = new java.io.File(System.getProperty("user.home"), "OneDrive\\Desktop");
        if (!desktop.exists()) desktop = new java.io.File(System.getProperty("user.home"), "Desktop");
        if (!desktop.exists()) desktop = new java.io.File(System.getProperty("user.home"));
        java.io.File file = new java.io.File(desktop, "Bill_" + billID + ".png");
        try {
            file.getParentFile().mkdirs(); // ensure folder exists
            java.io.FileOutputStream fos = new java.io.FileOutputStream(file);
            javax.imageio.ImageIO.write(img, "png", fos);
            fos.flush();
            fos.close();
            JOptionPane.showMessageDialog(this,
                    "Image saved:\n" + file.getAbsolutePath(),
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
            warn("Could not save image: " + ex.getMessage());
        }
    }

    private void showPastBills() {
        List<Bill> bills = billDAO.getBillsByCashier(cashier.getCashierID());
        if (bills.isEmpty()) { JOptionPane.showMessageDialog(this, "No bills found."); return; }

        DefaultTableModel m = new DefaultTableModel(new String[]{"Bill ID", "Date", "Total (Rs.)"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        for (Bill b : bills)
            m.addRow(new Object[]{b.getBillID(), b.getBillDate(), String.format("%.2f", b.getTotalAmount())});
        JTable t = new JTable(m);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton btnView = new JButton("View Selected");
        JButton btnEdit = new JButton("Edit Selected");

        btnView.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row >= 0) showBillReceipt(bills.get(row));
            else JOptionPane.showMessageDialog(this, "Select a bill first.");
        });

        btnEdit.addActionListener(e -> {
            int row = t.getSelectedRow();
            if (row < 0) { JOptionPane.showMessageDialog(this, "Select a bill first."); return; }
            Bill selected = bills.get(row);
            // Reload fresh from DB so items are up to date
            Bill fresh = billDAO.getBillByID(selected.getBillID());
            if (fresh == null) { warn("Could not load bill."); return; }
            showEditBill(fresh, bills, m, t);
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        btnRow.add(btnView);
        btnRow.add(btnEdit);

        JPanel p = new JPanel(new BorderLayout(4, 8));
        p.add(new JScrollPane(t), BorderLayout.CENTER);
        p.add(btnRow, BorderLayout.SOUTH);
        p.setPreferredSize(new Dimension(460, 300));
        JOptionPane.showMessageDialog(this, p, "Past Bills", JOptionPane.PLAIN_MESSAGE);
    }

    private void showEditBill(Bill bill, List<Bill> bills, DefaultTableModel billsModel, JTable billsTable) {
        // Build editable items list — deep copy so cancelling discards changes
        List<BillItem> editItems = new ArrayList<>();
        for (BillItem bi : bill.getItems())
            editItems.add(new BillItem(bi.getBillItemID(), bi.getBillID(), bi.getProductID(),
                    bi.getProductName(), bi.getQuantitySold(), bi.getUnitPriceAtSale()));

        DefaultTableModel editModel = new DefaultTableModel(
                new String[]{"#", "Product", "Qty", "Unit Price", "Subtotal"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable editTable = new JTable(editModel);
        editTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JLabel lblEditTotal = new JLabel("", SwingConstants.RIGHT);
        lblEditTotal.setFont(new Font("SansSerif", Font.BOLD, 14));

        // Helper to refresh the edit table
        Runnable refreshEdit = () -> {
            editModel.setRowCount(0);
            double total = 0;
            int i = 1;
            for (BillItem item : editItems) {
                editModel.addRow(new Object[]{i++, item.getProductName(), item.getQuantitySold(),
                        String.format("Rs. %.2f", item.getUnitPriceAtSale()),
                        String.format("Rs. %.2f", item.getSubtotal())});
                total += item.getSubtotal();
            }
            lblEditTotal.setText(String.format("Total: Rs. %.2f", total));
        };
        refreshEdit.run();

        // ── Add item row ──────────────────────────────────────────────
        JComboBox<Product> cmbP = new JComboBox<>();
        for (Product p : productDAO.getAll()) if (p.isAvailable()) cmbP.addItem(p);
        cmbP.setPreferredSize(new Dimension(200, 26));
        JTextField txtQ = new JTextField("1", 4);

        JButton btnAddItem = new JButton("Add");
        btnAddItem.addActionListener(e -> {
            Product sel = (Product) cmbP.getSelectedItem();
            if (sel == null) return;
            int qty;
            try {
                qty = Integer.parseInt(txtQ.getText().trim());
                if (qty <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) { warn("Enter a valid quantity."); return; }

            // Stock check: account for qty already in the edit list
            int alreadyInBill = 0;
            BillItem existing = null;
            for (BillItem bi : editItems)
                if (bi.getProductID() == sel.getProductID()) { existing = bi; alreadyInBill = bi.getQuantitySold(); break; }

            if (alreadyInBill + qty > sel.getStockQty()) {
                warn("Not enough stock. Available: " + sel.getStockQty()); return;
            }
            if (existing != null) {
                existing.setQuantitySold(existing.getQuantitySold() + qty);
            } else {
                editItems.add(new BillItem(0, bill.getBillID(), sel.getProductID(),
                        sel.getName(), qty, sel.getPrice()));
            }
            refreshEdit.run();
        });

        JButton btnRemoveItem = new JButton("Remove Selected");
        btnRemoveItem.addActionListener(e -> {
            int row = editTable.getSelectedRow();
            if (row >= 0) { editItems.remove(row); refreshEdit.run(); }
        });

        // ── Layout ───────────────────────────────────────────────────
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        addRow.setBorder(BorderFactory.createTitledBorder("Add Item"));
        addRow.add(new JLabel("Product:")); addRow.add(cmbP);
        addRow.add(new JLabel("Qty:"));     addRow.add(txtQ);
        addRow.add(btnAddItem);

        JPanel editPanel = new JPanel(new BorderLayout(4, 6));
        editPanel.add(addRow, BorderLayout.NORTH);
        editPanel.add(new JScrollPane(editTable), BorderLayout.CENTER);

        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.add(btnRemoveItem, BorderLayout.WEST);
        bottomRow.add(lblEditTotal, BorderLayout.EAST);
        editPanel.add(bottomRow, BorderLayout.SOUTH);
        editPanel.setPreferredSize(new Dimension(520, 380));

        int result = JOptionPane.showConfirmDialog(this, editPanel,
                "Edit Bill #" + bill.getBillID(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            if (editItems.isEmpty()) { warn("Bill must have at least one item."); return; }
            boolean ok = billDAO.updateBill(bill, editItems, productDAO);
            if (ok) {
                // Refresh the past bills table row
                for (int i = 0; i < bills.size(); i++) {
                    if (bills.get(i).getBillID() == bill.getBillID()) {
                        bills.set(i, bill);
                        billsModel.setValueAt(String.format("%.2f", bill.getTotalAmount()), i, 2);
                        break;
                    }
                }
                JOptionPane.showMessageDialog(this, "Bill #" + bill.getBillID() + " updated successfully.");
                loadProducts(); // refresh main dropdown in case stock changed
            } else {
                warn("Failed to update bill.");
            }
        }
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Warning", JOptionPane.WARNING_MESSAGE);
    }
}