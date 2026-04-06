package com.inventory.ui;

import com.inventory.dao.CashierDAO;
import com.inventory.dao.ProductDAO;
import com.inventory.model.Admin;
import com.inventory.model.Cashier;
import com.inventory.model.Product;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

public class AdminDashboard extends JFrame {

    private final Admin admin;
    private final CashierDAO cashierDAO = new CashierDAO();
    private final ProductDAO productDAO = new ProductDAO();

    private DefaultTableModel cashierModel;
    private DefaultTableModel productModel;

    public AdminDashboard(Admin admin) {
        this.admin = admin;
        setTitle("Admin Dashboard — " + admin.getUsername());
        setSize(800, 560);
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
        menuBar.add(menuAccount);
        setJMenuBar(menuBar);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Manage Cashiers", buildCashierPanel());
        tabs.addTab("Manage Products", buildProductPanel());
        add(tabs);
    }

    // ─── Validation helpers ───────────────────────────────────────────────────────

    /**
     * Returns an error message if the name starts with a digit, otherwise null.
     * Character.isDigit checks position 0 — catches 0 through 9.
     * e.g. "123" → blocked, "noodles(123)" → allowed.
     */
    private String checkName(String name, String fieldLabel) {
        if (name.isEmpty())
            return fieldLabel + " cannot be empty.";
        if (Character.isDigit(name.charAt(0)))
            return fieldLabel + " must not start with a number.\n"
                    + "Tip: put the number in brackets instead.\n"
                    + "Example: \"noodles(123)\" not \"123\".";
        return null;
    }

    /**
     * Returns an error message if password is shorter than 4 characters, otherwise null.
     * length() < 4 rejects "abc", "12", "a" etc.
     */
    private String checkPassword(String password) {
        if (password.isEmpty())
            return "Password cannot be empty.";
        if (password.length() < 4)
            return "Password must be at least 4 characters long.";
        return null;
    }

    /**
     * Returns an error message if initial stock is below 20, otherwise null.
     * Only applied when ADDING a product — editing is not restricted.
     */
    private String checkInitialStock(int qty) {
        if (qty < 20)
            return "Initial stock must be at least 20 units. You entered: " + qty + ".";
        return null;
    }

    /**
     * If the base username already exists in the list, keeps appending a number
     * until a free one is found. e.g. "ram" taken → tries "ram2", "ram3", ...
     */
    private String suggestUniqueCashierName(String base, List<String> existing) {
        if (!existing.contains(base)) return base;
        int suffix = 2;
        while (existing.contains(base + suffix)) suffix++;
        return base + suffix;
    }

    // ─── Cashier Panel ───────────────────────────────────────────────────────────

    private JPanel buildCashierPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        cashierModel = new DefaultTableModel(new String[]{"ID", "Username"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(cashierModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane cashierScroll = new JScrollPane(table);
        panel.add(cashierScroll, BorderLayout.CENTER);
        loadCashiers();

        JPanel form = new JPanel(new GridLayout(2, 2, 6, 6));
        JTextField txtUser = new JTextField();
        JPasswordField txtPass = new JPasswordField();
        form.add(new JLabel("Username:")); form.add(txtUser);
        form.add(new JLabel("Password:")); form.add(txtPass);

        // Populate fields when row selected; clear when deselected
        table.getSelectionModel().addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    txtUser.setText((String) cashierModel.getValueAt(row, 1));
                    txtPass.setText("");
                } else {
                    txtUser.setText(""); txtPass.setText("");
                }
            }
        });

        // Click on empty space -> deselect
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (table.rowAtPoint(e.getPoint()) < 0) table.clearSelection();
            }
        });
        cashierScroll.getViewport().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                table.clearSelection();
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton btnAdd  = new JButton("Add");
        JButton btnEdit = new JButton("Edit Selected");
        JButton btnDel  = new JButton("Delete Selected");

        btnAdd.addActionListener(e -> {
            String u = txtUser.getText().trim();
            String p = new String(txtPass.getPassword()).trim();

            String nameErr = checkName(u, "Username");
            if (nameErr != null) { warn(nameErr); return; }

            String passErr = checkPassword(p);
            if (passErr != null) { warn(passErr); return; }

            List<String> existing = cashierDAO.getAllUsernames();
            if (existing.contains(u)) {
                String suggested = suggestUniqueCashierName(u, existing);
                int choice = JOptionPane.showConfirmDialog(this,
                        "Username \"" + u + "\" already exists.\n"
                                + "Suggested unique name: \"" + suggested + "\"\n\n"
                                + "Click Yes to use \"" + suggested + "\", or No to type a different name.",
                        "Duplicate Username", JOptionPane.YES_NO_OPTION);
                if (choice == JOptionPane.YES_OPTION) u = suggested;
                else return;
            }

            if (cashierDAO.create(u, p)) {
                loadCashiers(); txtUser.setText(""); txtPass.setText("");
            } else {
                warn("Could not add cashier.");
            }
        });

        btnEdit.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { warn("Select a cashier to edit."); return; }
            int id = (int) cashierModel.getValueAt(row, 0);
            String u = txtUser.getText().trim();
            String p = new String(txtPass.getPassword()).trim();

            String nameErr = checkName(u, "Username");
            if (nameErr != null) { warn(nameErr); return; }

            String passErr = checkPassword(p);
            if (passErr != null) { warn(passErr); return; }

            String currentName = (String) cashierModel.getValueAt(row, 1);
            if (!u.equals(currentName)) {
                List<String> existing = cashierDAO.getAllUsernames();
                if (existing.contains(u)) {
                    String suggested = suggestUniqueCashierName(u, existing);
                    int choice = JOptionPane.showConfirmDialog(this,
                            "Username \"" + u + "\" already exists.\n"
                                    + "Suggested: \"" + suggested + "\"\n\nUse suggested name?",
                            "Duplicate Username", JOptionPane.YES_NO_OPTION);
                    if (choice == JOptionPane.YES_OPTION) u = suggested;
                    else return;
                }
            }

            if (cashierDAO.update(id, u, p)) {
                loadCashiers();
                txtUser.setText(""); txtPass.setText(""); // Bug fix 2: clear fields after edit
            } else warn("Update failed.");
        });

        btnDel.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { warn("Select a cashier to delete."); return; }
            int id = (int) cashierModel.getValueAt(row, 0);
            if (JOptionPane.showConfirmDialog(this, "Delete this cashier?", "Confirm",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                if (cashierDAO.delete(id)) loadCashiers();
                else warn("Delete failed. Cashier may have existing bills.");
            }
        });

        btnPanel.add(btnAdd); btnPanel.add(btnEdit); btnPanel.add(btnDel);

        JPanel south = new JPanel(new BorderLayout());
        south.add(form, BorderLayout.CENTER);
        south.add(btnPanel, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void loadCashiers() {
        cashierModel.setRowCount(0);
        for (Cashier c : cashierDAO.getAll()) {
            cashierModel.addRow(new Object[]{c.getCashierID(), c.getUsername()});
        }
    }

    // ─── Product Panel ────────────────────────────────────────────────────────────

    private JPanel buildProductPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        productModel = new DefaultTableModel(new String[]{"ID", "Name", "Price (Rs.)", "Stock"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(productModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane productScroll = new JScrollPane(table);
        panel.add(productScroll, BorderLayout.CENTER);
        loadProducts();

        JPanel form = new JPanel(new GridLayout(3, 2, 6, 6));
        JTextField txtName  = new JTextField();
        JTextField txtPrice = new JTextField();
        JTextField txtStock = new JTextField();
        form.add(new JLabel("Name:"));      form.add(txtName);
        form.add(new JLabel("Price:"));     form.add(txtPrice);
        form.add(new JLabel("Stock Qty:")); form.add(txtStock);

        // Populate fields when row selected; clear when deselected
        table.getSelectionModel().addListSelectionListener(ev -> {
            if (!ev.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    txtName.setText((String) productModel.getValueAt(row, 1));
                    txtPrice.setText(String.valueOf(productModel.getValueAt(row, 2)));
                    txtStock.setText(String.valueOf(productModel.getValueAt(row, 3)));
                } else {
                    txtName.setText(""); txtPrice.setText(""); txtStock.setText("");
                }
            }
        });

        // Click on empty space -> deselect
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                if (table.rowAtPoint(e.getPoint()) < 0) table.clearSelection();
            }
        });
        productScroll.getViewport().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                table.clearSelection();
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton btnAdd  = new JButton("Add");
        JButton btnEdit = new JButton("Edit Selected");
        JButton btnDel  = new JButton("Delete Selected");

        btnAdd.addActionListener(e -> {
            try {
                String n  = txtName.getText().trim();
                double pr = Double.parseDouble(txtPrice.getText().trim());
                int    st = Integer.parseInt(txtStock.getText().trim());

                String nameErr = checkName(n, "Product name");
                if (nameErr != null) { warn(nameErr); return; }

                String stockErr = checkInitialStock(st);
                if (stockErr != null) { warn(stockErr); return; }

                if (productDAO.nameExists(n)) {
                    warn("A product named \"" + n + "\" already exists.\n"
                            + "Please add a distinguishing detail to the name.\n"
                            + "Example: \"" + n + " (red)\", \"" + n + " (spicy)\", \"" + n + " 500g\"");
                    return;
                }

                if (productDAO.create(n, pr, st)) {
                    loadProducts();
                    txtName.setText(""); txtPrice.setText(""); txtStock.setText("");
                } else {
                    warn("Could not add product.");
                }
            } catch (NumberFormatException ex) {
                warn("Price and Stock must be valid numbers.");
            }
        });

        btnEdit.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { warn("Select a product to edit."); return; }
            try {
                int    id = (int) productModel.getValueAt(row, 0);
                String n  = txtName.getText().trim();
                double pr = Double.parseDouble(txtPrice.getText().trim());
                int    st = Integer.parseInt(txtStock.getText().trim());

                String nameErr = checkName(n, "Product name");
                if (nameErr != null) { warn(nameErr); return; }

                // Skip duplicate check if name is unchanged — editing "Noodles"
                // should not block itself from saving.
                String currentName = (String) productModel.getValueAt(row, 1);
                if (!n.equalsIgnoreCase(currentName) && productDAO.nameExists(n)) {
                    warn("A product named \"" + n + "\" already exists.\n"
                            + "Please add a distinguishing detail.\n"
                            + "Example: \"" + n + " (blue)\", \"" + n + " large\"");
                    return;
                }

                if (productDAO.update(id, n, pr, st)) {
                    loadProducts();
                    txtName.setText(""); txtPrice.setText(""); txtStock.setText(""); // Bug fix 2: clear fields after edit
                } else warn("Update failed.");
            } catch (NumberFormatException ex) {
                warn("Price and Stock must be valid numbers.");
            }
        });

        btnDel.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) { warn("Select a product to delete."); return; }
            int id = (int) productModel.getValueAt(row, 0);
            String name = (String) productModel.getValueAt(row, 1);
            if (JOptionPane.showConfirmDialog(this,
                    "Delete product \"" + name + "\"?\n",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                productDAO.unlinkFromBills(id); // nullify FK in bill_items first
                if (productDAO.delete(id)) {
                    loadProducts();
                    table.clearSelection();
                } else warn("Delete failed.");
            }
        });

        btnPanel.add(btnAdd); btnPanel.add(btnEdit); btnPanel.add(btnDel);

        JPanel south = new JPanel(new BorderLayout());
        south.add(form, BorderLayout.CENTER);
        south.add(btnPanel, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void loadProducts() {
        productModel.setRowCount(0);
        for (Product p : productDAO.getAll()) {
            productModel.addRow(new Object[]{p.getProductID(), p.getName(), p.getPrice(), p.getStockQty()});
        }
    }

    private void warn(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Validation Error", JOptionPane.WARNING_MESSAGE);
    }
}