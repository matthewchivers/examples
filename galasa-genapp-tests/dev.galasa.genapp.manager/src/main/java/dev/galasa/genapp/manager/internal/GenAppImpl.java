package dev.galasa.genapp.manager.internal;

import java.nio.charset.Charset;
import java.util.Random;

import dev.galasa.ICredentialsUsernamePassword;
import dev.galasa.framework.spi.IConfidentialTextService;
import dev.galasa.framework.spi.IFramework;
import dev.galasa.genapp.manager.GenAppManagerException;
import dev.galasa.genapp.manager.ICommercialPolicy;
import dev.galasa.genapp.manager.ICustomer;
import dev.galasa.genapp.manager.IEndowmentPolicy;
import dev.galasa.genapp.manager.IGenApp;
import dev.galasa.genapp.manager.IHousePolicy;
import dev.galasa.genapp.manager.IMotorPolicy;
import dev.galasa.genapp.manager.internal.MotorPolicyImpl;
import dev.galasa.zos.IZosImage;
import dev.galasa.zos.ZosManagerException;
import dev.galasa.zos3270.FieldNotFoundException;
import dev.galasa.zos3270.ITerminal;
import dev.galasa.zos3270.KeyboardLockedException;
import dev.galasa.zos3270.TextNotFoundException;
import dev.galasa.zos3270.Zos3270Exception;

public class GenAppImpl implements IGenApp {

    private ITerminal terminal;
    private String applID;
    private int port;
    private String hostAddress;
    private ICredentialsUsernamePassword creds;

    private final String PREFIX = "GENAPP";

    /**
     * These variables are referring to the JSON-requests that are allowed by the
     * GenApp installation
     */
    private final String getCustomer = "getCustomerDetails";
    private final String addCustomer = "addCustomerDetails";
    private final String getCommercial = "getCommercialPolicyDetails";
    private final String addCommercial = "addCommercialPolicyDetails";
    private final String getEndowment = "getEndowmentPolicyDetails";
    private final String addEndowment = "addEndowmentPolicyDetails";
    private final String getHouse = "getHousePolicyDetails";
    private final String addHouse = "addHousePolicyDetails";
    private final String getMotor = "getMotorPolicyDetails";
    private final String addMotor = "addMotorPolicyDetails";

    public GenAppImpl(ITerminal terminal3270, String applid, int port, IZosImage image, IFramework framework)
            throws GenAppManagerException {
        try {
            this.applID = applid;
            this.port = port;
            this.terminal = terminal3270;
            this.hostAddress = "http://" + image.getIpHost().getIpv4Hostname() + ":" + this.port;
            this.creds = (ICredentialsUsernamePassword) image.getDefaultCredentials();
            IConfidentialTextService cts = framework.getConfidentialTextService();
            cts.registerText(creds.getUsername(), "GenApp username");
            cts.registerText(creds.getPassword(), "GenApp password");
        } catch (ZosManagerException e) {
            throw new GenAppManagerException("Issue generating GenApp Instance", e);
        }

        logon();
    }

    @Override
    public String getApplId() {
        return this.applID;
    }

    @Override
    public String getAddress() {
        return this.hostAddress;
    }

    @Override
    public String provisionCustomerName() {
        return Integer.toHexString(new Random().nextInt()).substring(0, 6);
    }

    @Override
    public String getAddCustomerPath() {
        return this.PREFIX + "/" + this.addCustomer;
    }

    @Override
    public String getInquireCustomerPath() {
        return this.PREFIX + "/" + this.getCustomer;
    }

    @Override
    public String getAddMotorPolicyPath() {
        return this.PREFIX + "/" + this.addMotor;
    }

    @Override
    public String getInquireMotorPolicyPath() {
        return this.PREFIX + "/" + this.getMotor;
    }

    @Override
    public String getAddEndowmentPolicyPath() {
        return this.PREFIX + "/" + this.addEndowment;
    }

    @Override
    public String getInquireEndowmentPolicyPath() {
        return this.PREFIX + "/" + this.getEndowment;
    }

    @Override
    public String getAddHousePolicyPath() {
        return this.PREFIX + "/" + this.addHouse;
    }

    @Override
    public String getInquireHousePolicyPath() {
        return this.PREFIX + "/" + this.getHouse;
    }

    @Override
    public String getAddCommericalPolicyPath() {
        return this.PREFIX + "/" + this.addCommercial;
    }

    @Override
    public String getInquireCommericalPolicyPath() {
        return this.PREFIX + "/" + this.getCommercial;
    }

    /**
     * Interacting with GenApp through its 3270-connection to be able to inquire a
     * specific customer through its unique identifier ID
     * 
     * @throws GenAppManagerException
     */
    public ICustomer inquireCustomer(int id) throws GenAppManagerException {
        String defaultId = "0000000000";
        String customerId = Integer.toString(id);

        try {
            terminal.waitForKeyboard().type("ssc1").enter().waitForKeyboard()
                    .positionCursorToFieldContaining("Cust Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - customerId.length()) + customerId)
                    .positionCursorToFieldContaining("Select Option").tab().type("1").enter().waitForKeyboard();

            if (terminal.retrieveScreen().contains("No data was returned.")) {
                terminal.pf3().waitForKeyboard().clear().waitForKeyboard();
                return null;
            }
        
            String firstName = terminal.retrieveFieldTextAfterFieldWithString("First").trim();
            String lastName = terminal.retrieveFieldTextAfterFieldWithString("Last").trim();
            String dob = terminal.retrieveFieldTextAfterFieldWithString("DOB").trim();
            String houseName = terminal.retrieveFieldTextAfterFieldWithString("House Name").trim();
            String houseNum = terminal.retrieveFieldTextAfterFieldWithString("House Number").trim();
            String postcode = terminal.retrieveFieldTextAfterFieldWithString("Postcode").trim();
            String homePhone = terminal.retrieveFieldTextAfterFieldWithString("Phone: Home").trim();
            String mobPhone = terminal.retrieveFieldTextAfterFieldWithString("Phone: Mob").trim();
            String emailAddress = terminal.retrieveFieldTextAfterFieldWithString("Email  Addr").trim();

            ICustomer customer = new CustomerImpl(this, id, firstName, lastName, dob, houseName, houseNum, postcode,
                    homePhone, mobPhone, emailAddress);

            terminal.pf3().waitForKeyboard().clear().waitForKeyboard();

            return customer;
        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue Inquiring Customer", e);
        }
    }

    /**
     * Interacting with GenApp through its 3270-connection to be able to add a clean
     * new customer
     * 
     * @throws GenAppManagerException
     */
    public ICustomer addCustomer() throws GenAppManagerException {
        try {
            terminal.waitForKeyboard().type("ssc1").enter().waitForKeyboard()
                    .positionCursorToFieldContaining("Select Option").tab().type("2").enter().waitForKeyboard();

            String customerNum = terminal.retrieveFieldTextAfterFieldWithString("Cust Number");

            terminal.pf3().waitForKeyboard().clear().waitForKeyboard();

            return new CustomerImpl(this, Integer.parseInt(customerNum), "", "", "", "", "", "", "", "", "");
        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue Inquiring Customer", e);
        }
    }

    /**
     * Interacting with GenApp through its 3270-connection to update a specific
     * value of the GenApp-data
     * 
     * @throws GenAppManagerException
     */
    public ICustomer updateCustomer(ICustomer customer, String field, String value) throws GenAppManagerException {
        try {
            String defaultId = "0000000000";

            String customerId = Integer.toString(customer.getCustomerNumber());

            terminal.waitForKeyboard().type("ssc1").enter().waitForKeyboard()
                    .positionCursorToFieldContaining("Cust Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - customerId.length()) + customerId)
                    .positionCursorToFieldContaining("Select Option").tab().type("4").enter().waitForKeyboard();

            fillField(field, value);
            terminal.enter().waitForKeyboard();

            terminal.pf3().waitForKeyboard().clear().waitForKeyboard();

            return inquireCustomer(customer.getCustomerNumber());

        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue Adding Customer", e);
        }
    }

    public IMotorPolicy inquireMotorPolicy(ICustomer customer, int policyNumber) throws GenAppManagerException {
        String defaultId = "0000000000";
        String customerId = Integer.toString(customer.getCustomerNumber());
        String policyId = Integer.toString(policyNumber);

        try {
            terminal.waitForKeyboard().type("ssp1").enter().waitForKeyboard()
                    .positionCursorToFieldContaining("Policy Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - policyId.length()) + policyId)
                    .positionCursorToFieldContaining("Cust Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - customerId.length()) + customerId)
                    .positionCursorToFieldContaining("Select Option").tab().type("1").enter().waitForKeyboard();

            if (terminal.retrieveScreen().contains("No data was returned.")) {
                terminal.pf3().waitForKeyboard().clear().waitForKeyboard();
                return null;
            }

            String carMake = terminal.retrieveFieldTextAfterFieldWithString("Postcode").trim();
            String carModel = terminal.retrieveFieldTextAfterFieldWithString("Customer Name").trim();
            int carValue = Integer.parseInt(terminal.retrieveFieldTextAfterFieldWithString("Status").trim());
            String carRegistration = terminal.retrieveFieldTextAfterFieldWithString("Registration").trim();

            terminal.pf3().waitForKeyboard().clear().waitForKeyboard();

            return new MotorPolicyImpl(customer, policyNumber, carMake, carModel, carValue , carRegistration);

        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue Inquiring Commercial Policy", e);
        }
    }

    public IMotorPolicy createMotorPolicy(ICustomer customer, String carMake, String carModel, int carValue, String carRegistration) throws GenAppManagerException {
        int policyNumber = 0;
        while (inquireMotorPolicy(customer, policyNumber) != null) {
            policyNumber++;
        }

        String defaultId = "0000000000";
        String defaultValue = "000000";
        String customerId = Integer.toString(customer.getCustomerNumber());
        String policyId = Integer.toString(policyNumber);
        String carValueStr = Integer.toString(carValue);

        try {
            terminal.waitForKeyboard().type("ssp1").enter().waitForKeyboard()
                    .positionCursorToFieldContaining("Policy Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - policyId.length()) + policyId)
                    .positionCursorToFieldContaining("Cust Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - customerId.length()) + customerId);

            fillField("Car Value", defaultValue.substring(0, defaultValue.length() - carValueStr.length()) + carValueStr);
            fillField("Car Make", carMake);
            fillField("Car Model", carModel);
            terminal.positionCursorToFieldContaining("Select Option").tab().type("2").enter().waitForKeyboard();

            terminal.pf3().waitForKeyboard().clear().waitForKeyboard();

            if (!terminal.retrieveScreen().contains("New Motor Policy Inserted")) {
                return null;
            }

            return new MotorPolicyImpl(customer, policyNumber, carMake, carModel, carValue , carRegistration);
        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue Inquiring Commercial Policy", e);
        }
    }

    public IEndowmentPolicy inquireEndowmentPolicy(ICustomer customer, int policyNumber)
            throws GenAppManagerException {
        String defaultId = "0000000000";
        String customerId = Integer.toString(customer.getCustomerNumber());
        String policyId = Integer.toString(policyNumber);

        try {
            terminal.waitForKeyboard().type("ssp2").enter().waitForKeyboard()
                    .positionCursorToFieldContaining("Policy Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - policyId.length()) + policyId)
                    .positionCursorToFieldContaining("Cust Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - customerId.length()) + customerId)
                    .positionCursorToFieldContaining("Select Option").tab().type("1").enter().waitForKeyboard();

            if (terminal.retrieveScreen().contains("No data was returned.")) {
                terminal.pf3().waitForKeyboard().clear().waitForKeyboard();
                return null;
            }

            String fundName = terminal.retrieveFieldTextAfterFieldWithString("Fund Name").trim();
            String lifeAssured = terminal.retrieveFieldTextAfterFieldWithString("Life Assured").trim();
            String withProfits = terminal.retrieveFieldTextAfterFieldWithString("With Profits").trim();
            String equities = terminal.retrieveFieldTextAfterFieldWithString("Equities").trim();
            String managedFunds = terminal.retrieveFieldTextAfterFieldWithString("Managed Funds").trim();

            terminal.pf3().waitForKeyboard().clear().waitForKeyboard();

            return new EndowmentPolicyImpl(customer, policyNumber, fundName, lifeAssured, withProfits, equities, managedFunds);

        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue Inquiring Endowment Policy", e);
        }

    }

    public IEndowmentPolicy createEndowmentPolicy(ICustomer customer, String fundName, String lifeAssured,
            String withProfits, String equities, String managedFunds) throws GenAppManagerException {
        int policyNumber = 1;
        while (inquireEndowmentPolicy(customer, policyNumber) != null) {
            policyNumber++;
        }
        String defaultId = "0000000000";
        String customerId = Integer.toString(customer.getCustomerNumber());
        String policyId = Integer.toString(policyNumber);
        try {
            terminal.waitForKeyboard().type("ssp2").enter().waitForKeyboard()
                    .positionCursorToFieldContaining("Policy Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - policyId.length()) + policyId)
                    .positionCursorToFieldContaining("Cust Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - customerId.length()) + customerId);
            fillField("Fund Name", fundName);
            fillField("Life Assured", lifeAssured);
            fillField("With Profits", withProfits);
            fillField("Equities", equities);
            terminal.positionCursorToFieldContaining("Select Option").tab().type("2").enter().waitForKeyboard();
            terminal.pf3().waitForKeyboard().clear().waitForKeyboard();
            if (terminal.retrieveScreen().contains("Policy Inserted"))
                return new EndowmentPolicyImpl(customer, policyNumber, fundName, lifeAssured, withProfits, equities,
                        managedFunds);
            else
                return null;
        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue Adding Commercial Policy", e);
        }
    }

    public IHousePolicy inquireHousePolicy(ICustomer customer, int policyNumber) throws GenAppManagerException {
        String defaultId = "0000000000";
        String customerId = Integer.toString(customer.getCustomerNumber());
        String policyId = Integer.toString(policyNumber);

        try {
            terminal.waitForKeyboard().type("ssp3").enter().waitForKeyboard()
                    .positionCursorToFieldContaining("Policy Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - policyId.length()) + policyId)
                    .positionCursorToFieldContaining("Cust Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - customerId.length()) + customerId)
                    .positionCursorToFieldContaining("Select Option").tab().type("1").enter().waitForKeyboard();

            if (terminal.retrieveScreen().contains("No data was returned.")) {
                terminal.pf3().waitForKeyboard().clear().waitForKeyboard();
                return null;
            }

            String propertyType = terminal.retrieveFieldTextAfterFieldWithString("Property Type").trim();
            int bedRooms = Integer.parseInt(terminal.retrieveFieldTextAfterFieldWithString("Bedrooms").trim());
            int houseValue = Integer.parseInt(terminal.retrieveFieldTextAfterFieldWithString("House Value").trim());
            String houseName = terminal.retrieveFieldTextAfterFieldWithString("House Name").trim();
            String houseNumber = terminal.retrieveFieldTextAfterFieldWithString("House Number").trim();
            String postcode = terminal.retrieveFieldTextAfterFieldWithString("Postcode").trim();

            terminal.pf3().waitForKeyboard().clear().waitForKeyboard();

            return new HousePolicyImpl(customer, policyNumber, propertyType, bedRooms, houseValue, houseName, houseNumber, postcode);

        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue Inquiring Commercial Policy", e);
        }
    }

    public IHousePolicy createHousePolicy(ICustomer customer, String propertyType, int bedRooms, int houseValue, String houseName, String houseNumber, String postcode) throws GenAppManagerException {
        int policyNumber = 0;
        while(inquireHousePolicy(customer, policyNumber) != null) {
            policyNumber++;
        }

        String defaultId = "0000000000";
        String defaultBedrooms = "000";
        String defaultValue = "00000000";
        String customerId = Integer.toString(customer.getCustomerNumber());
        String policyId = Integer.toString(policyNumber);
        String bedroomsStr = Integer.toString(bedRooms);
        String houseValueStr = Integer.toString(houseValue);

        try {
            terminal.waitForKeyboard().type("ssp3").enter().waitForKeyboard()
                    .positionCursorToFieldContaining("Policy Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - policyId.length()) + policyId)
                    .positionCursorToFieldContaining("Cust Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - customerId.length()) + customerId)
                    .positionCursorToFieldContaining("Bedrooms").tab()
                    .type(defaultBedrooms.substring(0, defaultBedrooms.length() - bedroomsStr.length()) + bedroomsStr)
                    .positionCursorToFieldContaining("House Value").tab()
                    .type(defaultValue.substring(0, defaultValue.length() - houseValueStr.length()) + houseValueStr);
                    

            fillField("Property Type", propertyType);
            fillField("House Name", houseName);
            fillField("House Number", houseNumber);
            fillField("Postcode", postcode);

            terminal.positionCursorToFieldContaining("Select Option").tab().type("2").enter()
                    .waitForKeyboard();

                

            terminal.pf3().waitForKeyboard().clear().waitForKeyboard();

            if (!terminal.retrieveScreen().contains("New Motor Policy Inserted")) {
                return null;
            }

            return new HousePolicyImpl(customer, policyNumber, propertyType, bedRooms, houseValue, houseName, houseNumber, postcode);
        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue Inquiring Commercial Policy", e);
        }
    }


    

    public ICommercialPolicy inquireCommercialPolicy(ICustomer customer, int policyNumber)
            throws GenAppManagerException {
        String defaultId = "0000000000";
        String customerId = Integer.toString(customer.getCustomerNumber());
        String policyId = Integer.toString(policyNumber);

        try {
            terminal.waitForKeyboard().type("ssp4").enter().waitForKeyboard();
            terminal.positionCursorToFieldContaining("Policy Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - policyId.length()) + policyId)
                    .positionCursorToFieldContaining("Cust Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - customerId.length()) + customerId)
                    .positionCursorToFieldContaining("Select Option").tab().type("1").enter()
                    .waitForKeyboard();
            
            if (terminal.retrieveScreen().contains("No data was returned.")) {
                terminal.pf3().waitForKeyboard().clear().waitForKeyboard();
                return null;
            }

            String postcode = terminal.retrieveFieldTextAfterFieldWithString("Postcode").trim();
            String customerName = terminal.retrieveFieldTextAfterFieldWithString("Customer Name").trim();
            String status = terminal.retrieveFieldTextAfterFieldWithString("Status").trim();

            terminal.pf3().waitForKeyboard().clear().waitForKeyboard();

            return new CommercialPolicyImpl(customer, policyNumber, postcode, customerName, status);
        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue Inquiring Commercial Policy", e);
        }

    }

    public ICommercialPolicy createCommercialPolicy(ICustomer customer, String postcode, String customerName,
            String status) throws GenAppManagerException {
        int policyNumber = 1;
        while (inquireCommercialPolicy(customer, policyNumber) != null) {
            policyNumber++;
        }
        String defaultId = "0000000000";
        String customerId = Integer.toString(customer.getCustomerNumber());
        String policyId = Integer.toString(policyNumber);
        try {
            terminal.waitForKeyboard().type("ssp4").enter().waitForKeyboard()
                    .positionCursorToFieldContaining("Policy Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - policyId.length()) + policyId)
                    .positionCursorToFieldContaining("Cust Number").tab()
                    .type(defaultId.substring(0, defaultId.length() - customerId.length()) + customerId);
            fillField("Postcode", postcode);
            fillField("Customer Name", customerName);
            fillField("Status", status);
            terminal.positionCursorToFieldContaining("Select Option").tab().type("2").enter().waitForKeyboard();
            terminal.pf3().waitForKeyboard().clear().waitForKeyboard();
            if (terminal.retrieveScreen().contains("Policy Inserted"))
                return new CommercialPolicyImpl(customer, policyNumber, postcode, customerName, status);
            else
                return null;
        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue Adding Commercial Policy", e);
        }
    }
    /**
     * A static way to log in to the Application ID that is assigned through the
     * cps.properties
     * 
     * @throws GenAppManagerException
     */
    private void logon() throws GenAppManagerException {
        try {
            terminal.waitForKeyboard().type("logon applid(" + this.applID + ")").enter().waitForTextInField("Userid")
                    .pf3().waitForTextInField("Sign-on is terminated").clear().waitForKeyboard().type("cesl").enter()
                    .waitForTextInField("Userid").positionCursorToFieldContaining("Userid").tab()
                    .type(creds.getUsername()).positionCursorToFieldContaining("Password").tab()
                    .type(creds.getPassword()).enter().waitForKeyboard().clear().waitForKeyboard();
        } catch (InterruptedException | Zos3270Exception e) {
            throw new GenAppManagerException("Issue logging into GenApp", e);
        }
    }

    private void fillField(String field, String value)
            throws TextNotFoundException, GenAppManagerException, FieldNotFoundException, KeyboardLockedException {
        int fieldLength = terminal.retrieveFieldTextAfterFieldWithString(field).length();
        if(value.length() > fieldLength)
            throw new GenAppManagerException("Value " + value + " too long for field " + field + ". Must be " + fieldLength + " or fewer characters");

        StringBuffer defaultBuffer = new StringBuffer();
        while(defaultBuffer.length() < fieldLength) {
            defaultBuffer.append(" ");
        }

        String extendedValue = value + defaultBuffer.toString().substring(value.length(), fieldLength);
        
        terminal.positionCursorToFieldContaining(field).tab()
                .type(extendedValue);
    }

}