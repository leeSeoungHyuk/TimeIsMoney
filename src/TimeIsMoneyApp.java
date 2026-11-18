import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/** TimeIsMoney: a local-first Java Swing calendar ledger. */
public class TimeIsMoneyApp extends JFrame {
    private static final Color NAVY = new Color(244, 247, 251);
    private static final Color PANEL = Color.WHITE;
    private static final Color TEAL = new Color(37, 99, 235);
    private static final Color MUTED = new Color(102, 112, 133);
    private static final Color INK = new Color(31, 41, 55);
    private static final Color BORDER = new Color(226, 232, 240);
    private static final Color[] CATEGORY_COLORS = {
            new Color(66, 214, 179), new Color(96, 165, 250), new Color(251, 191, 36),
            new Color(244, 114, 182), new Color(167, 139, 250), new Color(251, 146, 60),
            new Color(45, 212, 191), new Color(248, 113, 113)
    };
    private static final String[] DEFAULT_CATEGORIES = {"식비", "통신비", "교통비", "주거/공과금", "쇼핑", "의료", "여가", "기타"};
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN);
    private static final Locale KOREAN = Locale.KOREA;

    private final Path dataDirectory = Paths.get(System.getProperty("user.dir"), "data");
    private final Path expenseFile = dataDirectory.resolve("expenses.csv");
    private final Path incomeFile = dataDirectory.resolve("daily-income.csv");
    private final Path allowanceFile = dataDirectory.resolve("allowances.csv");
    private final Path configFile = dataDirectory.resolve("settings.properties");
    private final AccountBook accountBook = new AccountBook();
    private final ExpenseTableModel expenseTableModel = new ExpenseTableModel(accountBook);

    private final JComboBox<String> salaryType = new JComboBox<>(new String[]{"시급", "연봉"});
    private final JTextField salaryAmount = new JTextField("13000", 8);
    private final JCheckBox[] scheduleEnabled = new JCheckBox[7];
    private final JTextField[] scheduleStart = new JTextField[7];
    private final JTextField[] scheduleEnd = new JTextField[7];
    private final JCheckBox[] breakEnabled = new JCheckBox[7];
    private final JTextField[] breakStart = new JTextField[7];
    private final JTextField[] breakEnd = new JTextField[7];
    private final JCheckBox compareEnabled = new JCheckBox("비교");
    private final JComboBox<String> comparisonProfile = new JComboBox<>(new String[]{
            "대한민국 대통령 (2026)", "대한민국 국회의원 (2026)", "이재용 (삼성전자 급여)", "사용자 정의"
    });
    private final JTextField comparisonName = new JTextField("대한민국 대통령", 10);
    private final JTextField comparisonAnnual = new JTextField("271770000", 9);
    private final JLabel comparisonReadout = new JLabel("비교 모드 꺼짐");

    private final JLabel dayName = new JLabel();
    private final JTextField dailyIncomeInput = new JTextField(10);
    private final JTextField allowanceInput = new JTextField(10);
    private final JLabel allowanceHint = new JLabel("용돈·보너스·기타 수입을 날짜별로 추가할 수 있습니다.");
    private final JLabel dailyIncomeHint = new JLabel("직접 입력한 하루 수입은 달력에 저장됩니다.");
    private final JLabel timerIncomeValue = new JLabel("₩0");
    private final JLabel timerElapsedValue = new JLabel("근무 진행률 0%");
    private final JLabel timerRateValue = new JLabel("초당 ₩0.00");
    private final JLabel statusValue = new JLabel("날짜를 선택해 수입 또는 지출을 기록하세요.");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JComboBox<String> expenseCategory = new JComboBox<>(DEFAULT_CATEGORIES);
    private final JTextField expenseAmount = new JTextField(9);
    private final JTextField expenseMemo = new JTextField(12);
    private final JTable expenseTable = new JTable(expenseTableModel);
    private final JPanel calendarGrid = new JPanel();
    private JSplitPane calendarDrawer;
    private JPanel dayDrawer;
    private JDialog comparisonDialog;
    private JLabel comparisonAmountValue;
    private boolean dayDrawerOpen;
    private javax.swing.Timer drawerAnimation;
    private final JLabel monthLabel = new JLabel();
    private final JLabel monthIncomeValue = new JLabel("₩0");
    private final JLabel monthExpenseValue = new JLabel("₩0");
    private final JLabel monthNetValue = new JLabel("₩0");

    private YearMonth shownMonth = YearMonth.now();
    private LocalDate selectedDate = LocalDate.now();
    private final javax.swing.Timer timer;
    private static final String[] DAY_KEYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};
    private static final String[] DAY_NAMES = {"월요일", "화요일", "수요일", "목요일", "금요일", "토요일", "일요일"};
    private final boolean[] savedScheduleEnabled = {true, true, true, true, true, false, false};
    private final String[] savedScheduleStart = {"09:00", "09:00", "09:00", "09:00", "09:00", "09:00", "09:00"};
    private final String[] savedScheduleEnd = {"18:00", "18:00", "18:00", "18:00", "18:00", "18:00", "18:00"};
    private final boolean[] savedBreakEnabled = {true, true, true, true, true, false, false};
    private final String[] savedBreakStart = {"12:00", "12:00", "12:00", "12:00", "12:00", "12:00", "12:00"};
    private final String[] savedBreakEnd = {"13:00", "13:00", "13:00", "13:00", "13:00", "13:00", "13:00"};

    public TimeIsMoneyApp() {
        super("TimeIsMoney | 달력 가계부");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1120, 760));
        setSize(1370, 930);
        setLocationRelativeTo(null);
        getContentPane().setBackground(NAVY);
        setLayout(new BorderLayout(14, 14));

        expenseCategory.setEditable(true);
        expenseCategory.setSelectedItem("식비");
        timer = new javax.swing.Timer(1000, e -> tick());
        timer.setCoalesce(true);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildWorkspace(), BorderLayout.CENTER);
        add(buildStatus(), BorderLayout.SOUTH);

        loadSettings();
        loadData();
        timer.start();
        updateSelectedDay();
        refreshAll();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                saveSettings();
                timer.stop();
                dispose();
            }
        });
    }

    private JComponent buildHeader() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(20, 24, 0, 24));
        JPanel titlePanel = new JPanel(new GridLayout(2, 1, 0, 3));
        titlePanel.setOpaque(false);
        JLabel title = new JLabel("TimeIsMoney");
        title.setFont(new Font("Malgun Gothic", Font.BOLD, 26));
        title.setForeground(INK);
        JLabel sub = new JLabel("달력에서 하루 수입과 지출을 기록하고 한눈에 돌아보세요");
        sub.setFont(new Font("Malgun Gothic", Font.PLAIN, 13));
        sub.setForeground(MUTED);
        titlePanel.add(title); titlePanel.add(sub);
        root.add(titlePanel, BorderLayout.WEST);
        root.add(buildMonthSummary(), BorderLayout.EAST);
        return root;
    }

    private JPanel buildMonthSummary() {
        JPanel summary = new JPanel(new GridLayout(1, 3, 9, 0));
        summary.setOpaque(false);
        summary.add(summaryCard("이번 달 수입", monthIncomeValue, TEAL));
        summary.add(summaryCard("이번 달 지출", monthExpenseValue, new Color(251, 191, 36)));
        summary.add(summaryCard("이번 달 순수익", monthNetValue, INK));
        return summary;
    }

    private JPanel summaryCard(String title, JLabel value, Color color) {
        JPanel card = new JPanel(new GridLayout(2, 1, 0, 4));
        card.setBackground(PANEL);
        card.setBorder(new EmptyBorder(10, 14, 10, 14));
        JLabel label = new JLabel(title);
        label.setForeground(MUTED);
        label.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
        value.setForeground(color);
        value.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
        card.add(label); card.add(value);
        return card;
    }

    private JComponent buildWorkspace() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setOpaque(false);
        root.setBorder(new EmptyBorder(0, 24, 0, 24));
        root.add(buildHero(), BorderLayout.NORTH);
        dayDrawer = (JPanel) buildDayPanel();
        calendarDrawer = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildCalendarPanel(), dayDrawer);
        calendarDrawer.setContinuousLayout(true);
        calendarDrawer.setOneTouchExpandable(false);
        calendarDrawer.setResizeWeight(1.0);
        calendarDrawer.setDividerSize(0);
        calendarDrawer.setDividerLocation(1.0);
        dayDrawer.setMinimumSize(new Dimension(0, 0));
        root.add(calendarDrawer, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildHero() {
        JPanel hero = panel(); hero.setLayout(new BorderLayout(18, 0));
        hero.setBorder(new EmptyBorder(14, 18, 14, 18));
        JPanel stats = new JPanel(new GridLayout(2, 2, 8, 3)); stats.setOpaque(false);
        JLabel paceTitle = new JLabel("오늘의 수입 속도"); paceTitle.setForeground(MUTED);
        paceTitle.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        timerRateValue.setForeground(TEAL); timerRateValue.setFont(new Font("Malgun Gothic", Font.BOLD, 25));
        JLabel earnedTitle = new JLabel("오늘 누적 예상 수입"); earnedTitle.setForeground(MUTED);
        earnedTitle.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        timerIncomeValue.setForeground(INK); timerIncomeValue.setFont(new Font("Malgun Gothic", Font.BOLD, 23));
        stats.add(paceTitle); stats.add(earnedTitle); stats.add(timerRateValue); stats.add(timerIncomeValue);
        JPanel actions = new JPanel(new GridLayout(2, 2, 8, 7)); actions.setOpaque(false);
        JButton salarySettings = new JButton("급여 · 근무 일정"); styleButton(salarySettings, new Color(239, 246, 255), TEAL);
        salarySettings.addActionListener(e -> showSalarySettings());
        compareEnabled.setText("비교 보기"); compareEnabled.setOpaque(false); compareEnabled.setForeground(INK);
        compareEnabled.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
        compareEnabled.addActionListener(e -> toggleComparisonDialog());
        timerElapsedValue.setForeground(MUTED); timerElapsedValue.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        progressBar.setStringPainted(true); progressBar.setString("오늘 근무 진행 0%"); progressBar.setForeground(TEAL);
        progressBar.setBackground(new Color(239, 243, 249)); progressBar.setBorderPainted(false);
        actions.add(salarySettings); actions.add(compareEnabled);
        hero.add(stats, BorderLayout.CENTER); hero.add(actions, BorderLayout.EAST);
        JPanel footer = new JPanel(new BorderLayout(12, 4)); footer.setOpaque(false);
        footer.add(timerElapsedValue, BorderLayout.WEST); footer.add(progressBar, BorderLayout.CENTER);
        hero.add(footer, BorderLayout.SOUTH);
        return hero;
    }

    private JComponent buildCalendarPanel() {
        JPanel root = panel(); root.setLayout(new BorderLayout(8, 10));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        JPanel monthBar = new JPanel(new BorderLayout()); monthBar.setOpaque(false);
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0)); nav.setOpaque(false);
        JButton previous = new JButton("‹"); styleButton(previous, new Color(248, 250, 252), INK);
        JButton next = new JButton("›"); styleButton(next, new Color(248, 250, 252), INK);
        previous.addActionListener(e -> { shownMonth = shownMonth.minusMonths(1); refreshCalendar(); });
        next.addActionListener(e -> { shownMonth = shownMonth.plusMonths(1); refreshCalendar(); });
        monthLabel.setForeground(INK); monthLabel.setFont(new Font("Malgun Gothic", Font.BOLD, 18));
        nav.add(previous); nav.add(monthLabel); nav.add(next);
        JButton today = new JButton("오늘"); styleButton(today, new Color(248, 250, 252), INK);
        today.addActionListener(e -> { shownMonth = YearMonth.now(); selectDate(LocalDate.now()); });
        monthBar.add(nav, BorderLayout.WEST); monthBar.add(today, BorderLayout.EAST);
        root.add(monthBar, BorderLayout.NORTH);

        JPanel calendar = new JPanel(new BorderLayout(0, 6)); calendar.setOpaque(false);
        JPanel weekdays = new JPanel(new GridLayout(1, 7, 6, 0)); weekdays.setOpaque(false);
        String[] week = {"월", "화", "수", "목", "금", "토", "일"};
        for (int i = 0; i < week.length; i++) {
            JLabel label = new JLabel(week[i], SwingConstants.CENTER); label.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
            label.setForeground(i == 6 ? new Color(248, 113, 113) : i == 5 ? new Color(96, 165, 250) : MUTED); weekdays.add(label);
        }
        calendar.add(weekdays, BorderLayout.NORTH);
        calendarGrid.setOpaque(false);
        calendar.add(calendarGrid, BorderLayout.CENTER);
        root.add(calendar, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildDayPanel() {
        JPanel root = panel(); root.setLayout(new BorderLayout(10, 12));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        JPanel heading = new JPanel(new BorderLayout()); heading.setOpaque(false);
        dayName.setForeground(INK); dayName.setFont(new Font("Malgun Gothic", Font.BOLD, 16));
        JButton close = new JButton("×"); styleButton(close, new Color(248, 250, 252), INK);
        close.addActionListener(e -> closeDayDrawer());
        heading.add(dayName, BorderLayout.CENTER); heading.add(close, BorderLayout.EAST);
        root.add(heading, BorderLayout.NORTH);

        JPanel details = new JPanel(new BorderLayout(8, 10)); details.setOpaque(false);
        JPanel incomeBox = new JPanel(new BorderLayout(6, 8)); incomeBox.setBackground(new Color(239, 246, 255));
        incomeBox.setBorder(new EmptyBorder(12, 12, 12, 12));
        JLabel incomeTitle = new JLabel("하루 급여 기록"); incomeTitle.setForeground(TEAL); incomeTitle.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        JPanel incomeForm = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); incomeForm.setOpaque(false);
        styleInput(dailyIncomeInput);
        JButton saveIncome = new JButton("저장"); styleButton(saveIncome, TEAL, NAVY); saveIncome.addActionListener(e -> saveDailyIncome());
        JButton clearIncome = new JButton("예정 급여 사용"); styleButton(clearIncome, new Color(248, 250, 252), INK); clearIncome.addActionListener(e -> clearDailyIncome());
        incomeForm.add(new JLabel("₩")); incomeForm.getComponent(0).setForeground(INK);
        incomeForm.add(dailyIncomeInput); incomeForm.add(saveIncome); incomeForm.add(clearIncome);
        dailyIncomeHint.setForeground(MUTED); dailyIncomeHint.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
        incomeBox.add(incomeTitle, BorderLayout.NORTH); incomeBox.add(incomeForm, BorderLayout.CENTER); incomeBox.add(dailyIncomeHint, BorderLayout.SOUTH);
        JPanel allowanceBox = new JPanel(new BorderLayout(6, 6)); allowanceBox.setBackground(new Color(240, 253, 250));
        allowanceBox.setBorder(new EmptyBorder(10, 12, 10, 12));
        JLabel allowanceTitle = new JLabel("용돈 · 보너스 · 기타 수입"); allowanceTitle.setForeground(new Color(5, 150, 105)); allowanceTitle.setFont(new Font("Malgun Gothic", Font.BOLD, 13));
        JPanel allowanceForm = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0)); allowanceForm.setOpaque(false);
        styleInput(allowanceInput);
        JButton saveAllowance = new JButton("추가"); styleButton(saveAllowance, new Color(5, 150, 105), Color.WHITE); saveAllowance.addActionListener(e -> saveAllowance());
        JButton clearAllowance = new JButton("지우기"); styleButton(clearAllowance, new Color(248, 250, 252), INK); clearAllowance.addActionListener(e -> clearAllowance());
        JLabel won = new JLabel("₩"); won.setForeground(INK);
        allowanceForm.add(won); allowanceForm.add(allowanceInput); allowanceForm.add(saveAllowance); allowanceForm.add(clearAllowance);
        allowanceHint.setForeground(MUTED); allowanceHint.setFont(new Font("Malgun Gothic", Font.PLAIN, 10));
        allowanceBox.add(allowanceTitle, BorderLayout.NORTH); allowanceBox.add(allowanceForm, BorderLayout.CENTER); allowanceBox.add(allowanceHint, BorderLayout.SOUTH);
        JPanel incomeStack = new JPanel(new BorderLayout(0, 8)); incomeStack.setOpaque(false);
        incomeStack.add(incomeBox, BorderLayout.NORTH); incomeStack.add(allowanceBox, BorderLayout.CENTER);
        details.add(incomeStack, BorderLayout.NORTH);

        JPanel expenses = new JPanel(new BorderLayout(8, 8)); expenses.setOpaque(false);
        JPanel expHeader = new JPanel(new BorderLayout()); expHeader.setOpaque(false);
        JLabel expenseTitle = new JLabel("선택한 날의 지출"); expenseTitle.setForeground(INK); expenseTitle.setFont(new Font("Malgun Gothic", Font.BOLD, 14));
        JLabel categoryNote = new JLabel("항목별 색상은 달력 막대와 같아요"); categoryNote.setForeground(MUTED); categoryNote.setFont(new Font("Malgun Gothic", Font.PLAIN, 10));
        expHeader.add(expenseTitle, BorderLayout.WEST); expHeader.add(categoryNote, BorderLayout.SOUTH);
        expenses.add(expHeader, BorderLayout.NORTH);

        expenseTable.setFillsViewportHeight(true); expenseTable.setRowHeight(25); expenseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        expenseTable.setBackground(PANEL); expenseTable.setForeground(INK); expenseTable.setGridColor(BORDER);
        expenseTable.setSelectionBackground(new Color(239, 246, 255)); expenseTable.setSelectionForeground(INK);
        expenseTable.getTableHeader().setBackground(new Color(248, 250, 252)); expenseTable.getTableHeader().setForeground(INK);
        expenseTable.getTableHeader().setFont(new Font("Malgun Gothic", Font.BOLD, 11));
        expenseTable.getColumnModel().getColumn(1).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
                Component cell = super.getTableCellRendererComponent(table, value, selected, focus, row, col);
                if (!selected && value != null) cell.setForeground(StackedExpenseBar.colorFor(value.toString(), 0));
                return cell;
            }
        });
        JScrollPane scroll = new JScrollPane(expenseTable); scroll.getViewport().setBackground(PANEL);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        expenses.add(scroll, BorderLayout.CENTER);

        JPanel expenseForm = new JPanel(new GridBagLayout()); expenseForm.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(3, 2, 3, 2); c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2; c.weightx = 1; expenseForm.add(expenseCategory, c);
        c.gridy = 1; c.gridwidth = 1; c.weightx = 0.5; expenseForm.add(expenseAmount, c);
        c.gridx = 1; c.weightx = 0.8; expenseForm.add(expenseMemo, c);
        JButton addExpense = new JButton("지출 추가"); styleButton(addExpense, TEAL, NAVY); addExpense.addActionListener(e -> addExpense());
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2; c.weightx = 0; expenseForm.add(addExpense, c);
        JButton deleteExpense = new JButton("선택 항목 삭제"); styleButton(deleteExpense, new Color(248, 250, 252), INK);
        deleteExpense.addActionListener(e -> deleteSelectedExpense());
        c.gridy = 3; expenseForm.add(deleteExpense, c);
        expenseAmount.setToolTipText("금액(원)"); expenseMemo.setToolTipText("메모(선택)"); expenseMemo.setToolTipText("메모(선택)");
        expenses.add(expenseForm, BorderLayout.SOUTH);
        details.add(expenses, BorderLayout.CENTER);
        root.add(details, BorderLayout.CENTER);
        return root;
    }

    private JComponent buildStatus() {
        JPanel status = new JPanel(new BorderLayout()); status.setOpaque(false);
        status.setBorder(new EmptyBorder(0, 26, 14, 26));
        statusValue.setForeground(MUTED); statusValue.setFont(new Font("Malgun Gothic", Font.PLAIN, 11));
        JLabel storage = new JLabel("저장 위치: " + dataDirectory); storage.setForeground(new Color(113, 128, 150));
        storage.setFont(new Font("Malgun Gothic", Font.PLAIN, 10));
        status.add(statusValue, BorderLayout.WEST); status.add(storage, BorderLayout.EAST); return status;
    }

    private void openDayDrawer() {
        if (dayDrawerOpen) return;
        dayDrawerOpen = true;
        dayDrawer.setVisible(true);
        calendarDrawer.setDividerSize(7);
        animateDrawer(true);
    }

    private void closeDayDrawer() {
        if (!dayDrawerOpen) return;
        dayDrawerOpen = false;
        animateDrawer(false);
    }

    private void animateDrawer(boolean opening) {
        if (drawerAnimation != null && drawerAnimation.isRunning()) drawerAnimation.stop();
        int width = Math.max(calendarDrawer.getWidth(), 800);
        int end = opening ? width - 390 : width - calendarDrawer.getDividerSize();
        int start = calendarDrawer.getDividerLocation();
        if (start < 0 || start > width) start = opening ? width : width - 390;
        final int from = start;
        drawerAnimation = new javax.swing.Timer(12, null);
        drawerAnimation.addActionListener(e -> {
            int current = calendarDrawer.getDividerLocation();
            int next = current + (int) Math.round((end - current) * 0.34);
            if (Math.abs(end - next) < 4) {
                calendarDrawer.setDividerLocation(end);
                drawerAnimation.stop();
                if (!opening) { calendarDrawer.setDividerSize(0); dayDrawer.setVisible(false); calendarDrawer.setDividerLocation(1.0); }
            } else calendarDrawer.setDividerLocation(next);
        });
        calendarDrawer.setDividerLocation(from);
        drawerAnimation.start();
    }

    private void showSalarySettings() {
        JDialog dialog = new JDialog(this, "급여 설정", true);
        JPanel form = new JPanel(new GridBagLayout()); form.setBackground(PANEL);
        form.setBorder(new EmptyBorder(18, 18, 18, 18));
        GridBagConstraints c = new GridBagConstraints(); c.insets = new Insets(7, 5, 7, 5); c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0; c.gridy = 0; c.weightx = 0.35; JLabel typeLabel = new JLabel("급여 방식"); typeLabel.setForeground(MUTED); form.add(typeLabel, c);
        c.gridx = 1; c.weightx = 0.65; form.add(salaryType, c);
        c.gridx = 0; c.gridy = 1; JLabel amountLabel = new JLabel("시급 또는 연봉 (원)"); amountLabel.setForeground(MUTED); form.add(amountLabel, c);
        c.gridx = 1; styleInput(salaryAmount); form.add(salaryAmount, c);
        String[] weekdays = {"월", "화", "수", "목", "금", "토", "일"};
        c.gridx = 0; c.gridy = 2; c.gridwidth = 6; JLabel scheduleTitle = new JLabel("주간 근무 일정 (점심시간은 유급 근무시간에서 제외)"); scheduleTitle.setForeground(INK); scheduleTitle.setFont(new Font("Malgun Gothic", Font.BOLD, 14)); form.add(scheduleTitle, c);
        c.gridy = 3; c.gridwidth = 1; c.weightx = 0.2; JLabel dayHeader = new JLabel("요일"); dayHeader.setForeground(MUTED); form.add(dayHeader, c);
        c.gridx = 1; JLabel enabledHeader = new JLabel("근무"); enabledHeader.setForeground(MUTED); form.add(enabledHeader, c);
        c.gridx = 2; JLabel startHeader = new JLabel("시작"); startHeader.setForeground(MUTED); form.add(startHeader, c);
        c.gridx = 3; JLabel endHeader = new JLabel("종료"); endHeader.setForeground(MUTED); form.add(endHeader, c);
        c.gridx = 4; JLabel breakHeader = new JLabel("점심"); breakHeader.setForeground(MUTED); form.add(breakHeader, c);
        c.gridx = 5; JLabel breakTimeHeader = new JLabel("점심 시작 ~ 종료"); breakTimeHeader.setForeground(MUTED); form.add(breakTimeHeader, c);
        for (int i = 0; i < 7; i++) {
            final int index = i;
            if (scheduleEnabled[i] == null) { scheduleEnabled[i] = new JCheckBox(); scheduleStart[i] = new JTextField("09:00", 5); scheduleEnd[i] = new JTextField("18:00", 5); }
            if (breakEnabled[i] == null) { breakEnabled[i] = new JCheckBox(); breakStart[i] = new JTextField("12:00", 5); breakEnd[i] = new JTextField("13:00", 5); }
            scheduleEnabled[i].setOpaque(false); scheduleEnabled[i].setSelected(savedScheduleEnabled[index]);
            scheduleStart[i].setText(savedScheduleStart[index]); scheduleEnd[i].setText(savedScheduleEnd[index]);
            breakEnabled[i].setOpaque(false); breakEnabled[i].setSelected(savedBreakEnabled[index]);
            breakStart[i].setText(savedBreakStart[index]); breakEnd[i].setText(savedBreakEnd[index]);
            c.gridy = 4 + i; c.gridx = 0; JLabel day = new JLabel(weekdays[i] + "요일"); day.setForeground(INK); form.add(day, c);
            c.gridx = 1; form.add(scheduleEnabled[i], c);
            c.gridx = 2; styleInput(scheduleStart[i]); form.add(scheduleStart[i], c);
            c.gridx = 3; styleInput(scheduleEnd[i]); form.add(scheduleEnd[i], c);
            c.gridx = 4; form.add(breakEnabled[i], c);
            c.gridx = 5; JPanel breakTimes = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0)); breakTimes.setOpaque(false);
            styleInput(breakStart[i]); styleInput(breakEnd[i]); breakTimes.add(breakStart[i]); breakTimes.add(new JLabel("~")); breakTimes.add(breakEnd[i]); form.add(breakTimes, c);
        }
        JLabel note = new JLabel("지난 근무일은 예정 급여로 자동 계산됩니다. 날짜별 입력 금액은 자동 계산보다 우선합니다."); note.setForeground(MUTED);
        c.gridy = 11; c.gridx = 0; c.gridwidth = 6; form.add(note, c);
        JButton save = new JButton("저장"); styleButton(save, TEAL, Color.WHITE);
        save.addActionListener(e -> {
            try { userSalary(); validateSchedule(); saveSettings(); refreshAll(); statusValue.setText("급여 및 근무 일정을 저장했습니다."); dialog.dispose(); }
            catch (IllegalArgumentException ex) { warning(ex.getMessage()); }
        });
        c.gridy = 12; c.gridx = 0; c.gridwidth = 6; c.anchor = GridBagConstraints.EAST; form.add(save, c);
        dialog.setContentPane(form); dialog.pack(); dialog.setMinimumSize(new Dimension(700, 520)); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
    }

    private void toggleComparisonDialog() {
        if (compareEnabled.isSelected()) showComparisonDialog();
        else if (comparisonDialog != null) comparisonDialog.setVisible(false);
        saveSettings();
    }

    private void showComparisonDialog() {
        if (comparisonDialog == null) {
            comparisonDialog = new JDialog(this, "실시간 수입 비교", false);
            JPanel content = new JPanel(new GridBagLayout()); content.setBackground(PANEL);
            content.setBorder(new EmptyBorder(14, 14, 14, 14));
            GridBagConstraints c = new GridBagConstraints(); c.gridx = 0; c.gridy = 0; c.gridwidth = 2;
            c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; c.insets = new Insets(5, 4, 5, 4);
            JLabel title = new JLabel("오늘 타이머 기준 비교"); title.setForeground(TEAL); title.setFont(new Font("Malgun Gothic", Font.BOLD, 16)); content.add(title, c);
            c.gridy++; content.add(comparisonProfile, c);
            c.gridy++; c.gridwidth = 1; JLabel nameLabel = new JLabel("대상"); nameLabel.setForeground(MUTED); content.add(nameLabel, c);
            c.gridx = 1; content.add(comparisonName, c);
            c.gridx = 0; c.gridy++; JLabel incomeLabel = new JLabel("연봉 기준 (원)"); incomeLabel.setForeground(MUTED); content.add(incomeLabel, c);
            c.gridx = 1; content.add(comparisonAnnual, c);
            c.gridx = 0; c.gridy++; c.gridwidth = 2;
            comparisonAmountValue = new JLabel("비교 누적 ₩0 · 내 누적 ₩0"); comparisonAmountValue.setForeground(INK);
            comparisonAmountValue.setFont(new Font("Malgun Gothic", Font.BOLD, 13)); content.add(comparisonAmountValue, c);
            JLabel caveat = new JLabel("입력한 근무 일정으로 환산한 참고용 수치"); caveat.setForeground(MUTED); caveat.setFont(new Font("Malgun Gothic", Font.PLAIN, 10));
            c.gridy++; content.add(caveat, c);
            comparisonProfile.addActionListener(e -> { updateComparisonProfile(); refreshTimerReadings(); });
            java.awt.event.FocusAdapter updateOnEdit = new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) { refreshTimerReadings(); }
            };
            comparisonName.addFocusListener(updateOnEdit); comparisonAnnual.addFocusListener(updateOnEdit);
            comparisonDialog.setContentPane(content); comparisonDialog.setSize(360, 260); comparisonDialog.setResizable(false);
            comparisonDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            comparisonDialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosing(java.awt.event.WindowEvent e) { compareEnabled.setSelected(false); saveSettings(); }
            });
        }
        comparisonDialog.setLocation(getX() + getWidth() - comparisonDialog.getWidth() - 28, getY() + 100);
        refreshTimerReadings(); comparisonDialog.setVisible(true);
    }

    private JPanel panel() {
        JPanel p = new JPanel(); p.setBackground(PANEL); p.setBorder(BorderFactory.createLineBorder(BORDER)); return p;
    }

    private void styleButton(JButton button, Color bg, Color fg) {
        button.setBackground(bg); button.setForeground(fg); button.setFocusPainted(false);
        button.setFont(new Font("Malgun Gothic", Font.BOLD, 11)); button.setBorder(new EmptyBorder(7, 10, 7, 10));
    }

    private void styleInput(JTextField field) {
        field.setBackground(Color.WHITE); field.setForeground(INK); field.setCaretColor(INK);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(5, 7, 5, 7)));
    }

    private void refreshAll() {
        refreshCalendar(); refreshDayTable(); refreshMonthSummary(); refreshTimerReadings();
    }

    private void refreshCalendar() {
        monthLabel.setText(shownMonth.format(MONTH_FORMAT));
        calendarGrid.removeAll();
        int days = shownMonth.lengthOfMonth();
        int offset = shownMonth.atDay(1).getDayOfWeek().getValue() - 1;
        int rows = (int) Math.ceil((offset + days) / 7.0);
        calendarGrid.setLayout(new GridLayout(rows, 7, 6, 6));
        double maxExpense = accountBook.expensesIn(shownMonth).stream().mapToDouble(Expense::amount).sum();
        maxExpense = accountBook.dailyTotals(shownMonth).values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        for (int i = 0; i < rows * 7; i++) {
            int day = i - offset + 1;
            if (day < 1 || day > days) calendarGrid.add(blankDay());
            else {
                LocalDate date = shownMonth.atDay(day);
                calendarGrid.add(new CalendarDayCell(date, accountBook, maxExpense,
                        date.equals(selectedDate), date.equals(LocalDate.now()), this::selectDate, this::incomeForDate));
            }
        }
        calendarGrid.revalidate(); calendarGrid.repaint();
    }

    private JPanel blankDay() {
        JPanel blank = new JPanel(); blank.setOpaque(false); blank.setBorder(BorderFactory.createLineBorder(new Color(45, 55, 70))); return blank;
    }

    private void selectDate(LocalDate date) {
        selectedDate = date;
        updateSelectedDay(); refreshAll(); openDayDrawer();
    }

    private void updateSelectedDay() {
        dayName.setText(selectedDate.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", KOREAN)));
        DailyIncome income = accountBook.incomeFor(selectedDate);
        dailyIncomeInput.setText(income == null ? "" : amountInput(income.amount()));
        dailyIncomeHint.setText(income == null ? "예정 급여가 자동 계산됩니다. 직접 입력하면 해당 날짜 금액으로 저장됩니다." : "저장된 하루 급여가 예정 급여보다 우선 적용 중입니다. 0원도 저장할 수 있어요.");
        double allowance = accountBook.allowanceFor(selectedDate);
        allowanceInput.setText(allowance <= 0 ? "" : amountInput(allowance));
        allowanceHint.setText(allowance <= 0 ? "용돈·보너스·기타 수입을 날짜별로 추가할 수 있습니다." : "이 날짜에 저장된 용돈·기타 수입: " + krw(allowance));
    }

    private double incomeForDate(LocalDate date) {
        DailyIncome saved = accountBook.incomeFor(date);
        double base;
        if (saved != null) base = saved.amount();
        else if (date.isAfter(LocalDate.now())) base = scheduledIncome(date, 1.0);
        else base = scheduledIncome(date, date.equals(LocalDate.now()) ? shiftProgress(date, LocalTime.now()) : 1.0);
        return base + accountBook.allowanceFor(date);
    }

    private void refreshDayTable() {
        expenseTableModel.setDate(selectedDate);
        dailyIncomeHint.setToolTipText("CSV 저장 파일: " + incomeFile);
    }

    private void refreshMonthSummary() {
        double income = 0;
        LocalDate today = LocalDate.now();
        for (int d = 1; d <= shownMonth.lengthOfMonth(); d++) {
            LocalDate date = shownMonth.atDay(d);
            if (!date.isAfter(today)) income += incomeForDate(date);
        }
        double expenses = accountBook.expensesIn(shownMonth).stream().mapToDouble(Expense::amount).sum();
        monthIncomeValue.setText(krw(income)); monthExpenseValue.setText(krw(expenses)); monthNetValue.setText(krw(income - expenses));
    }

    private void tick() {
        refreshTimerReadings(); refreshCalendar(); refreshMonthSummary();
    }

    private double timerIncome() {
        try { return scheduledIncome(LocalDate.now(), shiftProgress(LocalDate.now(), LocalTime.now())); }
        catch (IllegalArgumentException ignored) { return 0; }
    }

    private void refreshTimerReadings() {
        double income = timerIncome();
        timerIncomeValue.setText(krw(income));
        LocalDate today = LocalDate.now();
        double progress = shiftProgress(today, LocalTime.now());
        int percent = (int) Math.round(progress * 100);
        timerElapsedValue.setText("근무 진행률 " + percent + "%");
        progressBar.setValue(percent); progressBar.setString("오늘 근무 진행 " + percent + "%");
        double rate = 0;
        try { rate = userSalary().perSecond(); } catch (IllegalArgumentException ignored) { }
        timerRateValue.setText(String.format(KOREAN, "초당 ₩%,.2f", rate));
        if (compareEnabled.isSelected()) {
            double comparison = comparisonPerSecond() * scheduledSeconds(today) * progress;
            comparisonReadout.setText(comparisonName.getText().trim() + " 예상 누적 " + krw(comparison)
                    + " · 내 타이머 " + krw(income));
            if (comparisonAmountValue != null) comparisonAmountValue.setText("비교 누적 " + krw(comparison) + " · 내 누적 " + krw(income));
        }
    }

    private UserSalary userSalary() {
        double amount = positive(salaryAmount.getText(), "급여 금액");
        long weeklySeconds = 0;
        for (int i = 0; i < 7; i++) if (savedScheduleEnabled[i]) weeklySeconds += paidSecondsForIndex(i);
        if (salaryType.getSelectedIndex() == 1 && weeklySeconds == 0) weeklySeconds = 40 * 3600L;
        return new UserSalary(salaryType.getSelectedIndex() == 0, amount, weeklySeconds);
    }

    private void saveDailyIncome() {
        try {
            double amount = nonNegative(dailyIncomeInput.getText(), "하루 급여");
            accountBook.setIncome(new DailyIncome(selectedDate, amount));
            persistIncomes(); updateSelectedDay(); refreshAll();
            statusValue.setText(selectedDate + " 하루 급여를 저장했습니다.");
        } catch (IllegalArgumentException e) { warning(e.getMessage()); }
        catch (IOException e) { warning("하루 급여를 저장하지 못했습니다: " + e.getMessage()); }
    }

    private void saveAllowance() {
        try {
            double amount = nonNegative(allowanceInput.getText(), "용돈·기타 수입");
            accountBook.setAllowance(selectedDate, amount);
            persistAllowances(); updateSelectedDay(); refreshAll();
            statusValue.setText(selectedDate + " 용돈·기타 수입을 저장했습니다.");
        } catch (IllegalArgumentException e) { warning(e.getMessage()); }
        catch (IOException e) { warning("용돈·기타 수입을 저장하지 못했습니다: " + e.getMessage()); }
    }

    private void clearAllowance() {
        accountBook.removeAllowance(selectedDate);
        try { persistAllowances(); updateSelectedDay(); refreshAll(); statusValue.setText("선택한 날짜의 용돈·기타 수입을 지웠습니다."); }
        catch (IOException e) { warning("용돈·기타 수입 파일을 저장하지 못했습니다: " + e.getMessage()); }
    }

    private void clearDailyIncome() {
        accountBook.removeIncome(selectedDate);
        try { persistIncomes(); updateSelectedDay(); refreshAll(); statusValue.setText("직접 입력한 하루 급여 기록을 지웠습니다."); }
        catch (IOException e) { warning("하루 급여 파일을 저장하지 못했습니다: " + e.getMessage()); }
    }

    private void addExpense() {
        Object value = expenseCategory.getEditor().getItem();
        String category = value == null ? "" : value.toString().trim();
        if (category.isEmpty()) { warning("지출 항목을 입력하세요."); return; }
        try {
            double amount = positive(expenseAmount.getText(), "지출 금액");
            String memo = expenseMemo.getText().trim();
            LocalDateTime time = LocalDateTime.of(selectedDate, LocalTime.now());
            accountBook.addExpense(new Expense(time, category, amount, memo));
            persistExpenses(); expenseAmount.setText(""); expenseMemo.setText("");
            refreshAll(); statusValue.setText(selectedDate + " 지출을 저장했습니다.");
        } catch (IllegalArgumentException e) { warning(e.getMessage()); }
        catch (IOException e) { warning("지출을 저장하지 못했습니다: " + e.getMessage()); }
    }

    private void deleteSelectedExpense() {
        int row = expenseTable.getSelectedRow();
        if (row < 0) { warning("삭제할 지출을 먼저 선택하세요."); return; }
        Expense item = expenseTableModel.getExpenseAt(expenseTable.convertRowIndexToModel(row));
        accountBook.removeExpense(item);
        try { persistExpenses(); refreshAll(); statusValue.setText("선택한 지출을 삭제했습니다."); }
        catch (IOException e) { warning("지출 삭제를 저장하지 못했습니다: " + e.getMessage()); }
    }

    private void updateComparisonProfile() {
        int selected = comparisonProfile.getSelectedIndex();
        if (selected < 0) return;
        String[] names = {"대한민국 대통령", "대한민국 국회의원", "이재용", comparisonName.getText()};
        String[] values = {"271770000", "160925680", "0", comparisonAnnual.getText()};
        if (selected < 3) {
            comparisonName.setText(names[selected]); comparisonAnnual.setText(values[selected]);
            comparisonName.setEditable(false); comparisonAnnual.setEditable(false);
        } else { comparisonName.setEditable(true); comparisonAnnual.setEditable(true); }
        String tooltip = selected == 2 ? "삼성전자에서 받는 급여 기준이며, 배당 등 자산 소득은 포함하지 않습니다." :
                selected == 0 ? "2026년 대통령 연봉 기준. 수당·실수령액과 다를 수 있습니다." :
                selected == 1 ? "2026년 국회의원 세비 기준의 약 1억 6,100만 원." : "비교 대상 이름과 연봉을 직접 입력하세요.";
        comparisonProfile.setToolTipText(tooltip); comparisonAnnual.setToolTipText(tooltip);
    }

    private double comparisonPerSecond() {
        try {
            double annual = nonNegative(comparisonAnnual.getText(), "비교 연봉");
            UserSalary schedule = userSalary();
            long seconds = schedule.weeklySeconds() == 0 ? 40 * 3600L : schedule.weeklySeconds();
            return annual / (52.0 * seconds);
        } catch (IllegalArgumentException e) { return 0; }
    }

    private void validateSchedule() {
        for (int i = 0; i < 7; i++) {
            savedScheduleEnabled[i] = scheduleEnabled[i].isSelected();
            savedScheduleStart[i] = scheduleStart[i].getText().trim();
            savedScheduleEnd[i] = scheduleEnd[i].getText().trim();
            savedBreakEnabled[i] = breakEnabled[i].isSelected();
            savedBreakStart[i] = breakStart[i].getText().trim();
            savedBreakEnd[i] = breakEnd[i].getText().trim();
            try {
                LocalTime start = LocalTime.parse(savedScheduleStart[i], TIME_FORMAT);
                LocalTime end = LocalTime.parse(savedScheduleEnd[i], TIME_FORMAT);
                if (savedScheduleEnabled[i] && !end.isAfter(start)) throw new IllegalArgumentException(DAY_NAMES[i] + " 종료 시간은 시작 시간보다 늦어야 합니다.");
                LocalTime lunchStart = LocalTime.parse(savedBreakStart[i], TIME_FORMAT);
                LocalTime lunchEnd = LocalTime.parse(savedBreakEnd[i], TIME_FORMAT);
                if (savedBreakEnabled[i] && (!lunchEnd.isAfter(lunchStart) || lunchStart.isBefore(start) || lunchEnd.isAfter(end))) throw new IllegalArgumentException(DAY_NAMES[i] + " 점심시간은 근무시간 안에서 설정하세요.");
            } catch (java.time.DateTimeException ex) { throw new IllegalArgumentException(DAY_NAMES[i] + " 시간을 HH:mm 형식으로 입력하세요. (예: 09:00)"); }
        }
    }

    private long scheduledSeconds(LocalDate date) {
        int index = date.getDayOfWeek().getValue() - 1;
        if (!savedScheduleEnabled[index]) return 0;
        return paidSecondsForIndex(index);
    }

    private double shiftProgress(LocalDate date, LocalTime now) {
        int index = date.getDayOfWeek().getValue() - 1;
        if (!savedScheduleEnabled[index]) return 0;
        LocalTime start = LocalTime.parse(savedScheduleStart[index]);
        LocalTime end = LocalTime.parse(savedScheduleEnd[index]);
        if (!now.isAfter(start)) return 0;
        if (!now.isBefore(end)) return 1;
        long total = paidSecondsForIndex(index);
        if (total <= 0) return 0;
        return (double) paidElapsedSeconds(index, now) / total;
    }

    private long paidSecondsForIndex(int index) {
        LocalTime start = LocalTime.parse(savedScheduleStart[index]);
        LocalTime end = LocalTime.parse(savedScheduleEnd[index]);
        long total = Duration.between(start, end).getSeconds();
        if (savedBreakEnabled[index]) total -= breakOverlapSeconds(start, end, LocalTime.parse(savedBreakStart[index]), LocalTime.parse(savedBreakEnd[index]));
        return Math.max(0, total);
    }

    private long paidElapsedSeconds(int index, LocalTime now) {
        LocalTime start = LocalTime.parse(savedScheduleStart[index]);
        LocalTime end = LocalTime.parse(savedScheduleEnd[index]);
        LocalTime capped = now.isAfter(end) ? end : now;
        long elapsed = Math.max(0, Duration.between(start, capped).getSeconds());
        if (savedBreakEnabled[index]) elapsed -= breakOverlapSeconds(start, capped, LocalTime.parse(savedBreakStart[index]), LocalTime.parse(savedBreakEnd[index]));
        return Math.max(0, elapsed);
    }

    private static long breakOverlapSeconds(LocalTime shiftStart, LocalTime shiftEnd, LocalTime breakStart, LocalTime breakEnd) {
        if (!breakEnd.isAfter(breakStart)) return 0;
        LocalTime overlapStart = breakStart.isAfter(shiftStart) ? breakStart : shiftStart;
        LocalTime overlapEnd = breakEnd.isBefore(shiftEnd) ? breakEnd : shiftEnd;
        return overlapEnd.isAfter(overlapStart) ? Duration.between(overlapStart, overlapEnd).getSeconds() : 0;
    }

    private double scheduledIncome(LocalDate date, double progress) {
        long seconds = scheduledSeconds(date);
        try { return userSalary().perSecond() * seconds * Math.max(0, Math.min(1, progress)); }
        catch (IllegalArgumentException e) { return 0; }
    }

    private void saveSettingsWithMessage() {
        try { userSalary(); if (compareEnabled.isSelected()) positive(comparisonAnnual.getText(), "비교 연봉");
            saveSettings(); statusValue.setText("급여 및 비교 설정을 저장했습니다."); refreshAll(); }
        catch (IllegalArgumentException e) { warning(e.getMessage()); }
    }

    private void saveSettings() {
        Properties p = new Properties();
        p.setProperty("salary.type", salaryType.getSelectedIndex() == 0 ? "hourly" : "annual");
        p.setProperty("salary.amount", salaryAmount.getText().trim());
        for (int i = 0; i < 7; i++) {
            if (scheduleEnabled[i] != null) { savedScheduleEnabled[i] = scheduleEnabled[i].isSelected(); savedScheduleStart[i] = scheduleStart[i].getText().trim(); savedScheduleEnd[i] = scheduleEnd[i].getText().trim(); }
            if (breakEnabled[i] != null) { savedBreakEnabled[i] = breakEnabled[i].isSelected(); savedBreakStart[i] = breakStart[i].getText().trim(); savedBreakEnd[i] = breakEnd[i].getText().trim(); }
            p.setProperty("schedule." + DAY_KEYS[i] + ".enabled", Boolean.toString(savedScheduleEnabled[i]));
            p.setProperty("schedule." + DAY_KEYS[i] + ".start", savedScheduleStart[i]);
            p.setProperty("schedule." + DAY_KEYS[i] + ".end", savedScheduleEnd[i]);
            p.setProperty("schedule." + DAY_KEYS[i] + ".break.enabled", Boolean.toString(savedBreakEnabled[i]));
            p.setProperty("schedule." + DAY_KEYS[i] + ".break.start", savedBreakStart[i]);
            p.setProperty("schedule." + DAY_KEYS[i] + ".break.end", savedBreakEnd[i]);
        }
        p.setProperty("compare.profile", Integer.toString(Math.max(0, comparisonProfile.getSelectedIndex())));
        p.setProperty("compare.name", comparisonName.getText().trim()); p.setProperty("compare.annualIncome", comparisonAnnual.getText().trim());
        try { Files.createDirectories(dataDirectory); try (var writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) { p.store(writer, "TimeIsMoney settings"); } }
        catch (IOException e) { statusValue.setText("설정을 저장하지 못했습니다: " + e.getMessage()); }
    }

    private void loadSettings() {
        if (!Files.exists(configFile)) return;
        Properties p = new Properties();
        try (var reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            p.load(reader); salaryType.setSelectedIndex("annual".equals(p.getProperty("salary.type")) ? 1 : 0);
            salaryAmount.setText(p.getProperty("salary.amount", "13000"));
            for (int i = 0; i < 7; i++) {
                savedScheduleEnabled[i] = Boolean.parseBoolean(p.getProperty("schedule." + DAY_KEYS[i] + ".enabled", i < 5 ? "true" : "false"));
                savedScheduleStart[i] = p.getProperty("schedule." + DAY_KEYS[i] + ".start", "09:00");
                savedScheduleEnd[i] = p.getProperty("schedule." + DAY_KEYS[i] + ".end", "18:00");
                savedBreakEnabled[i] = Boolean.parseBoolean(p.getProperty("schedule." + DAY_KEYS[i] + ".break.enabled", i < 5 ? "true" : "false"));
                savedBreakStart[i] = p.getProperty("schedule." + DAY_KEYS[i] + ".break.start", "12:00");
                savedBreakEnd[i] = p.getProperty("schedule." + DAY_KEYS[i] + ".break.end", "13:00");
            }
            int profile = Integer.parseInt(p.getProperty("compare.profile", "0")); comparisonProfile.setSelectedIndex(Math.max(0, Math.min(3, profile)));
            comparisonName.setText(p.getProperty("compare.name", "대한민국 대통령")); comparisonAnnual.setText(p.getProperty("compare.annualIncome", "271770000"));
            compareEnabled.setSelected(false);
            updateComparisonProfile();
        } catch (IOException | RuntimeException e) { statusValue.setText("설정 파일을 읽지 못했습니다: " + e.getMessage()); }
    }

    private void loadData() {
        try {
            Files.createDirectories(dataDirectory);
            if (Files.exists(expenseFile)) accountBook.replaceExpenses(CsvStorage.readExpenses(expenseFile));
            if (Files.exists(incomeFile)) accountBook.replaceIncomes(CsvStorage.readIncomes(incomeFile));
            if (Files.exists(allowanceFile)) accountBook.replaceAllowances(CsvStorage.readAllowances(allowanceFile));
        } catch (IOException e) { statusValue.setText("저장된 기록을 읽지 못했습니다: " + e.getMessage()); }
    }

    private void persistExpenses() throws IOException { Files.createDirectories(dataDirectory); CsvStorage.writeExpenses(expenseFile, accountBook.allExpenses()); }
    private void persistIncomes() throws IOException { Files.createDirectories(dataDirectory); CsvStorage.writeIncomes(incomeFile, accountBook.allIncomes()); }
    private void persistAllowances() throws IOException { Files.createDirectories(dataDirectory); CsvStorage.writeAllowances(allowanceFile, accountBook.allAllowances()); }

    private void warning(String message) { JOptionPane.showMessageDialog(this, message, "입력 확인", JOptionPane.WARNING_MESSAGE); }
    private static double positive(String value, String label) { double n = parseNumber(value, label); if (n <= 0) throw new IllegalArgumentException(label + "은(는) 0보다 커야 합니다."); return n; }
    private static double nonNegative(String value, String label) { double n = parseNumber(value, label); if (n < 0) throw new IllegalArgumentException(label + "은(는) 0 이상이어야 합니다."); return n; }
    private static double parseNumber(String value, String label) {
        try { double n = Double.parseDouble(value.trim().replace(",", "")); if (!Double.isFinite(n)) throw new NumberFormatException(); return n; }
        catch (RuntimeException e) { throw new IllegalArgumentException(label + "을(를) 숫자로 입력하세요."); }
    }
    private static String krw(double amount) { return "₩" + String.format(KOREAN, "%,.0f", amount); }
    private static String amountInput(double amount) { return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TimeIsMoneyApp app = new TimeIsMoneyApp();
            app.setVisible(true);
            SwingUtilities.invokeLater(() -> app.calendarDrawer.setDividerLocation(app.calendarDrawer.getWidth()));
        });
    }
}

record UserSalary(boolean hourly, double amount, long weeklySeconds) {
    double perSecond() { return hourly ? amount / 3600.0 : amount / (52.0 * weeklySeconds); }
}
record DailyIncome(LocalDate date, double amount) { }
record Expense(LocalDateTime time, String category, double amount, String memo) { }

final class AccountBook {
    private final List<Expense> expenses = new ArrayList<>();
    private final Map<LocalDate, DailyIncome> incomes = new HashMap<>();
    private final Map<LocalDate, Double> allowances = new HashMap<>();
    void addExpense(Expense e) { expenses.add(e); }
    void removeExpense(Expense e) { expenses.remove(e); }
    List<Expense> allExpenses() { return List.copyOf(expenses); }
    List<Expense> expensesOn(LocalDate d) { return expenses.stream().filter(e -> e.time().toLocalDate().equals(d)).sorted(Comparator.comparing(Expense::time)).toList(); }
    List<Expense> expensesIn(YearMonth m) { return expenses.stream().filter(e -> YearMonth.from(e.time()).equals(m)).toList(); }
    Map<LocalDate, Double> dailyTotals(YearMonth m) { return expensesIn(m).stream().collect(Collectors.groupingBy(e -> e.time().toLocalDate(), Collectors.summingDouble(Expense::amount))); }
    Map<String, Double> categoryTotals(LocalDate d) { return expensesOn(d).stream().collect(Collectors.groupingBy(Expense::category, LinkedHashMap::new, Collectors.summingDouble(Expense::amount))); }
    double totalOn(LocalDate d) { return expensesOn(d).stream().mapToDouble(Expense::amount).sum(); }
    void setIncome(DailyIncome income) { incomes.put(income.date(), income); }
    void removeIncome(LocalDate date) { incomes.remove(date); }
    DailyIncome incomeFor(LocalDate date) { return incomes.get(date); }
    List<DailyIncome> allIncomes() { return incomes.values().stream().sorted(Comparator.comparing(DailyIncome::date)).toList(); }
    List<DailyIncome> incomesIn(YearMonth month) { return incomes.values().stream().filter(i -> YearMonth.from(i.date()).equals(month)).toList(); }
    void setAllowance(LocalDate date, double amount) { allowances.put(date, amount); }
    void removeAllowance(LocalDate date) { allowances.remove(date); }
    double allowanceFor(LocalDate date) { return allowances.getOrDefault(date, 0.0); }
    Map<LocalDate, Double> allAllowances() { return Map.copyOf(allowances); }
    void replaceExpenses(List<Expense> list) { expenses.clear(); expenses.addAll(list); }
    void replaceIncomes(List<DailyIncome> list) { incomes.clear(); for (DailyIncome i : list) incomes.put(i.date(), i); }
    void replaceAllowances(Map<LocalDate, Double> values) { allowances.clear(); allowances.putAll(values); }
}

final class CalendarDayCell extends JPanel {
    private static final Color PANEL = Color.WHITE;
    private static final Color TEAL = new Color(37, 99, 235);
    private static final Color MUTED = new Color(102, 112, 133);
    CalendarDayCell(LocalDate date, AccountBook book, double monthMaxExpense, boolean selected, boolean today,
                    java.util.function.Consumer<LocalDate> onClick, java.util.function.Function<LocalDate, Double> income) {
        setLayout(new BorderLayout(2, 4)); setBackground(selected ? new Color(239, 246, 255) : PANEL);
        setBorder(BorderFactory.createLineBorder(selected ? TEAL : today ? new Color(147, 197, 253) : new Color(226, 232, 240), selected || today ? 2 : 1));
        JLabel day = new JLabel(Integer.toString(date.getDayOfMonth()));
        day.setBorder(new EmptyBorder(4, 5, 0, 5)); day.setFont(new Font("Malgun Gothic", Font.BOLD, 12));
        int dow = date.getDayOfWeek().getValue(); day.setForeground(dow == 7 ? new Color(220, 38, 38) : dow == 6 ? new Color(37, 99, 235) : new Color(31, 41, 55));
        add(day, BorderLayout.NORTH);
        JPanel content = new JPanel(); content.setOpaque(false); content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel in = tinyLabel("수입 " + krw(income.apply(date))); in.setForeground(TEAL);
        JLabel out = tinyLabel("지출 " + krw(book.totalOn(date))); out.setForeground(MUTED);
        content.add(in); content.add(out); content.add(Box.createVerticalStrut(4));
        content.add(new StackedExpenseBar(book.categoryTotals(date), monthMaxExpense));
        add(content, BorderLayout.CENTER);
        Map<String, Double> cats = book.categoryTotals(date);
        String tip = cats.isEmpty() ? date + " · 기록 없음" : cats.entrySet().stream().map(e -> e.getKey() + " " + krw(e.getValue())).collect(Collectors.joining(" · "));
        setToolTipText(tip); day.setToolTipText(tip); content.setToolTipText(tip);
        MouseAdapter click = new MouseAdapter() { @Override public void mouseClicked(MouseEvent e) { onClick.accept(date); } };
        addMouseListener(click); day.addMouseListener(click); content.addMouseListener(click); in.addMouseListener(click); out.addMouseListener(click);
    }
    private static JLabel tinyLabel(String text) { JLabel l = new JLabel(text); l.setFont(new Font("Malgun Gothic", Font.PLAIN, 9)); l.setBorder(new EmptyBorder(0, 5, 0, 3)); return l; }
    private static String krw(double amount) { return "₩" + String.format(Locale.KOREA, "%,.0f", amount); }
}

final class StackedExpenseBar extends JComponent {
    private final Map<String, Double> values;
    private final double total;
    private final double max;
    StackedExpenseBar(Map<String, Double> values, double max) {
        this.values = values; this.max = max; this.total = values.values().stream().mapToDouble(Double::doubleValue).sum();
        setPreferredSize(new Dimension(70, 9)); setMinimumSize(new Dimension(20, 9)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 9));
        String tip = values.entrySet().stream().map(e -> e.getKey() + " " + String.format(Locale.KOREA, "%,.0f원", e.getValue())).collect(Collectors.joining(" / "));
        setToolTipText(tip.isEmpty() ? "지출 없음" : tip);
    }
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g); int w = getWidth(), h = getHeight();
        g.setColor(new Color(239, 243, 249)); g.fillRoundRect(0, 1, w, Math.max(5, h - 2), 6, 6);
        if (total <= 0 || max <= 0) return;
        int totalWidth = Math.max(3, (int) Math.round(w * Math.min(1.0, total / max)));
        int x = 0, index = 0;
        List<Map.Entry<String, Double>> entries = new ArrayList<>(values.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Double> e = entries.get(i);
            int segment = i == entries.size() - 1 ? totalWidth - x : (int) Math.round(totalWidth * e.getValue() / total);
            segment = Math.max(0, segment);
            g.setColor(colorFor(e.getKey(), index++)); if (segment > 0) g.fillRect(x, 1, segment, Math.max(5, h - 2));
            x += segment;
        }
    }
    static Color colorFor(String category, int fallback) { return CATEGORY_COLORS[Math.floorMod(category.hashCode(), CATEGORY_COLORS.length)]; }
    private static final Color[] CATEGORY_COLORS = {
            new Color(66, 214, 179), new Color(96, 165, 250), new Color(251, 191, 36), new Color(244, 114, 182),
            new Color(167, 139, 250), new Color(251, 146, 60), new Color(45, 212, 191), new Color(248, 113, 113)
    };
}

final class ExpenseTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"시간", "항목", "금액", "메모"};
    private final AccountBook book;
    private LocalDate date = LocalDate.now();
    ExpenseTableModel(AccountBook book) { this.book = book; }
    void setDate(LocalDate date) { this.date = date; fireTableDataChanged(); }
    Expense getExpenseAt(int row) { return book.expensesOn(date).get(row); }
    @Override public int getRowCount() { return book.expensesOn(date).size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }
    @Override public Object getValueAt(int row, int col) {
        Expense e = book.expensesOn(date).get(row);
        return switch (col) { case 0 -> e.time().format(DateTimeFormatter.ofPattern("HH:mm")); case 1 -> e.category(); case 2 -> "₩" + String.format(Locale.KOREA, "%,.0f", e.amount()); default -> e.memo(); };
    }
}

final class CsvStorage {
    static List<Expense> readExpenses(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8); List<Expense> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0 && (lines.get(i).startsWith("datetime,") || lines.get(i).startsWith("time,"))) continue;
            if (lines.get(i).isBlank()) continue;
            List<String> f = parse(lines.get(i));
            try {
                if (f.size() == 3) result.add(new Expense(LocalDateTime.parse(f.get(0)), f.get(1), Double.parseDouble(f.get(2)), ""));
                else if (f.size() >= 4) result.add(new Expense(LocalDateTime.parse(f.get(0)), f.get(1), Double.parseDouble(f.get(2)), f.get(3)));
                else throw new IllegalArgumentException();
            } catch (RuntimeException e) { throw new IOException("expenses.csv " + (i + 1) + "번째 줄을 읽을 수 없습니다.", e); }
        }
        return result;
    }
    static List<DailyIncome> readIncomes(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8); List<DailyIncome> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0 && lines.get(i).startsWith("date,")) continue; if (lines.get(i).isBlank()) continue;
            List<String> f = parse(lines.get(i));
            try { result.add(new DailyIncome(LocalDate.parse(f.get(0)), Double.parseDouble(f.get(1)))); }
            catch (RuntimeException e) { throw new IOException("daily-income.csv " + (i + 1) + "번째 줄을 읽을 수 없습니다.", e); }
        }
        return result;
    }
    static Map<LocalDate, Double> readAllowances(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8); Map<LocalDate, Double> result = new HashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            if (i == 0 && lines.get(i).startsWith("date,")) continue; if (lines.get(i).isBlank()) continue;
            List<String> f = parse(lines.get(i));
            try { result.put(LocalDate.parse(f.get(0)), Double.parseDouble(f.get(1))); }
            catch (RuntimeException e) { throw new IOException("allowances.csv " + (i + 1) + "번째 줄을 읽을 수 없습니다.", e); }
        }
        return result;
    }
    static void writeExpenses(Path path, List<Expense> all) throws IOException {
        List<String> lines = new ArrayList<>(); lines.add("datetime,category,amount,memo");
        for (Expense e : all) lines.add(csv(e.time().toString()) + "," + csv(e.category()) + "," + number(e.amount()) + "," + csv(e.memo()));
        writeAtomic(path, lines);
    }
    static void writeIncomes(Path path, List<DailyIncome> all) throws IOException {
        List<String> lines = new ArrayList<>(); lines.add("date,amount");
        for (DailyIncome i : all) lines.add(i.date() + "," + number(i.amount()));
        writeAtomic(path, lines);
    }
    static void writeAllowances(Path path, Map<LocalDate, Double> all) throws IOException {
        List<String> lines = new ArrayList<>(); lines.add("date,amount");
        all.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> lines.add(e.getKey() + "," + number(e.getValue())));
        writeAtomic(path, lines);
    }
    private static String number(double n) { return BigDecimal.valueOf(n).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }
    private static String csv(String s) { return "\"" + s.replace("\"", "\"\"") + "\""; }
    private static void writeAtomic(Path path, List<String> lines) throws IOException {
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(tmp, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static List<String> parse(String line) throws IOException {
        List<String> fields = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) { if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (c == '"') quoted = false; else field.append(c); }
            else if (c == '"' && field.isEmpty()) quoted = true;
            else if (c == ',') { fields.add(field.toString()); field.setLength(0); }
            else field.append(c);
        }
        if (quoted) throw new IOException("CSV에 닫히지 않은 따옴표가 있습니다."); fields.add(field.toString()); return fields;
    }
}
