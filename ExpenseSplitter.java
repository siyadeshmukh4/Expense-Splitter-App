import java.util.*; 
/**
 * SimpleExpenseSplitter
 * - Add people
 * - Add expenses split equally among selected participants
 * - Show net balances
 * - Show simple suggestions to settle up
 *
 * Net balance rule:
 *   - When someone pays $A for N participants, each participant owes A/N.
 *   - Payer gets +A to their balance (they fronted the cash).
 *   - Each participant gets -A/N to their balance (they owe their share).
 *   - If payer is also a participant, they still get +A then -A/N like everyone else.
 *     (That means they effectively paid more than their share.)
 */
public class ExpenseSplitter {
    private static final Scanner sc = new Scanner(System.in);
    private static final List<String> people = new ArrayList<>(); 
    private static final Map<String, Double> balance = new LinkedHashMap<>(); 
    
    /**
     * @param args
     */
    public static void main(String[] args)
    {
        System.out.println("------EXPENSE SPLITTER------"); 
        while(true)
        {
            System.out.println("\nMenu: ");
            System.out.println("1) Add Person"); 
            System.out.println("2) Add Expense");
            System.out.println("3) Show Balances");
            System.out.println("4) Settle Suggestions");
            System.out.println("5) Exit");
            String choice = sc.nextLine().trim(); 

            switch(choice)
            {
                case "1": addPerson(); break;  
                case "2": addExpense(); break; 
                case "3": showBalances(); break; 
                case "4": settleSuggestions(); break; 
                case "5": System.out.println("Bye!"); 
                default: System.out.println("Invalid Option. Try Again. "); 
            }

        }
       
    }
    private static void addPerson()
    {
        System.out.print("Enter Name: "); 
        String name = sc.nextLine().trim(); 
        if(name.isEmpty())
        {
            System.out.println("Name cannot be empty. Please enter a name."); 
            return; 
        }
        if(balance.containsKey(name))
        {
            System.out.println("That name already exists. Try again"); 
            return; 
        }
        people.add(name); 
        balance.put(name,0.0); 
        System.out.println("Added "+name+"."); 
    }
    private static void addExpense()
    {
        if (people.isEmpty()) {
            System.out.println("Add people first.");
            return;
        }
    
        System.out.print("Who paid? ");
        String payerRaw = sc.nextLine();
        String payer = norm(payerRaw);
    
        // Build a lookup from normalized -> display name
        Map<String,String> nameByNorm = new HashMap<>();
        for (String p : people) nameByNorm.put(norm(p), p);
    
      
        String payerDisplay = nameByNorm.get(payer);
    
        System.out.print("Amount (e.g., 42.50): ");
        String amtStr = sc.nextLine().trim();
        if (!amtStr.matches("\\d+(\\.\\d+)?")) {
            System.out.println("Invalid amount. Please enter a number like 10 or 12.50");
            return;
        }
        double amount = Double.parseDouble(amtStr);
        if (amount <= 0) {
            System.out.println("Amount must be positive.");
            return;
        }
    
        System.out.println("Enter participants (xx,yy,zz):");
        System.out.println("Leave empty to split among everyone.");
        System.out.println("Current people: " + String.join(", ", people));
        String line = sc.nextLine();
    
        // Build participant list
        List<String> participants = new ArrayList<>();
        if (line.trim().isEmpty()) {
            participants.addAll(people); // everyone (display names)
        } else {
            for (String s : line.split(",")) {
                String key = norm(s);
                if (key.isEmpty()) continue;
                if (!nameByNorm.containsKey(key)) {
                    System.out.println("Unknown participant: '" + s.trim() + "'. Current people: " + String.join(", ", people));
                    return;
                }
                participants.add(nameByNorm.get(key)); // store display name
            }
        }
        if (participants.isEmpty()) {
            System.out.println("No participants.");
            return;
        }
    
        // Do the split (use getOrDefault to avoid nulls)
        double share = amount / participants.size();
    
        double curPayer = balance.getOrDefault(payerDisplay, 0.0);
        balance.put(payerDisplay, round2(curPayer + amount));
    
        for (String pDisp : participants) {
            double cur = balance.getOrDefault(pDisp, 0.0);
            balance.put(pDisp, round2(cur - share));
        }
    
        System.out.printf("Recorded: %s paid $%.2f for %s. Everyone owes the person $%.2f%n",
                payerDisplay, amount, String.join(", ", participants), round2(share));

    }
    private static void showBalances()
    {
        if(people.isEmpty())
        {
            System.out.println("Add person first."); 
            return;
        }
        System.out.println("\n ------NET BALANCES------"); 
        boolean allZero = true; 
        for(String name : people)
        {
            double b = round2(balance.get(name)); 
            if(Math.abs(b)>=0.005)
            {
                allZero = false; 
            }
            System.out.printf("%-15s %8s $%.2f%n", name, (b >= 0 ? "is owed" : "owes"), Math.abs(b));
        }
        if(allZero)
        {
            System.out.println("Everyone is settled/matched up. 🎉"); 
        }
    }
    /**
     * Simple suggestion algorithm:
     *  - Make two lists: debtors (negative), creditors (positive)
     *  - Greedily match the biggest debtor with the biggest creditor
     *  - Create a payment equal to the smaller absolute amount
     *  - Update both amounts, repeat until all ~zero
     */
    private static void settleSuggestions()
    {
        List<PersonAmt> debtors = new ArrayList<>();
        List<PersonAmt> creditors = new ArrayList<>();

        for (String name : people) {
            double b = round2(balance.get(name));
            if (b < -0.004) debtors.add(new PersonAmt(name, -b));  // owes this much
            else if (b > 0.004) creditors.add(new PersonAmt(name, b)); // should receive this much
        }

        if (debtors.isEmpty() && creditors.isEmpty()) {
            System.out.println("No payments needed. All settled.");
            return;
        }

        // Sort largest first for fewer payments (still simple)
        debtors.sort((a,b)->Double.compare(b.amount, a.amount));
        creditors.sort((a,b)->Double.compare(b.amount, a.amount));

        System.out.println("\n--- Suggested Payments ---");
        int i = 0, j = 0;
        while (i < debtors.size() && j < creditors.size()) {
            PersonAmt d = debtors.get(i);
            PersonAmt c = creditors.get(j);
            double pay = round2(Math.min(d.amount, c.amount));

            System.out.printf("%s -> %s: $%.2f%n", d.name, c.name, pay);

            d.amount = round2(d.amount - pay);
            c.amount = round2(c.amount - pay);

            if (d.amount <= 0.004) i++;
            if (c.amount <= 0.004) j++;
        }

        System.out.println("\n(After these payments, everyone should be even.)");

    }
    private static double round2(double x)
    {
        return Math.round(x*100.0)/100.0; 
    }
    private static class PersonAmt
    {
        String name; 
        double amount; 
        PersonAmt(String n,Double a)
        {
            name = n; 
            amount = a; 
        }
    }
    private static String norm(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(); 
    }

}
