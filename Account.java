public class Account
{
    public Account(String name, String password)
    {
        this.name = name;
        this.password = password;
    }

    public String getName()
    {
        return name;
    }

    private final String name;
    private final String password;
}
