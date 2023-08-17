## Welcome to G3CodeGenerator
The source repository for the code generator project developed as part of video
[this youtube video series](https://www.youtube.com/playlist?list=PLRL-svxYmXggfzTyI6_wAM3mon4gYgyaL).

It contains the Annotations and Processor for generating the data-layer code which persist the data in a local file.

## How to use this library?
Locally checkout this git repository on your machine and run the following commands from the root folder of the repository.

```
mvn package

mvn install
```

Once you run the above commands, a jar file will be generated and put in your local maven repository.

Now, create a new maven project where you would like to use this library and add the following dependency in your pom
file.

```
<dependency>
      <groupId>com.gogettergeeks</groupId>
      <artifactId>G3CodeGenerator</artifactId>
      <version>1.0.0</version>
      <scope>provided</scope>
    </dependency>
```

And thats it! In your project, you can start using the annotations and all you need to do is build your project
to generate the data-layer and start using it!

## Available Annotations
**1. FileDBGenerated:** Use this annotation at any model class for which you want to generate the data-layer.
**2. Persisted:** Use this annotations on the fields (within your model class) you want to persist in file based DB.
**3. UniqueKey:** Use this annotation on primary key or unique key field which identifies the record uniquely.

## Example Usage
Consider the below Student model class.
```
import com.gogettergeeks.annotation.FileDBGenerated;
import com.gogettergeeks.annotation.Persisted;
import com.gogettergeeks.annotation.UniqueKey;

@FileDBGenerated
public class Student {
    @Persisted
    private String name;

    @Persisted
    @UniqueKey
    private int rollNumber;

    private double percentage;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getRollNumber() {
        return rollNumber;
    }

    public void setRollNumber(int rollNumber) {
        this.rollNumber = rollNumber;
    }
}
```

Here, we have three fields: (1) Name, (2) RollNumber, and (3) Percentage. Percentage field is not annotated with
@Persisted or @UniqueKey and hence this field won't be persisted in file.

Now simply build the project and you'll see 5 files getting generated.
1. StudentDao
2. StudentDaoImpl
3. StudentDtoGenerated
4. StudentRequestException
5. StudentServiceException

You can now use the generated files, a sample usage can be found below
```
public class Main 
{
    public static void main( String[] args ) throws Exception
    {
        System.out.println("====================== START ===========================");
        StudentDao dao = new StudentDaoImpl();
        List<Student> students = dao.getAll();
        for (Student s : students) {
            System.out.println("Name: " + s.getName() + " | RollNumber: " + s.getRollNumber());
        }
        
        System.out.println("==================== ADD =======================");
        Student s1 = new Student();
        s1.setName("John");
        s1.setRollNumber(1);

        Student s2 = new Student();
        s2.setName("Tom");
        s2.setRollNumber(2);

        Student s3 = new Student();
        s3.setName("Pop");
        s3.setRollNumber(3);

        Student s4 = new Student();
        s4.setName("Baburao");
        s4.setRollNumber(4);

        dao.add(s1);
        dao.add(s2);
        dao.add(s3);
        dao.add(s4);

        students = dao.getAll();

        for (Student s : students) {
            System.out.println("Name: " + s.getName() + " | RollNumber: " + s.getRollNumber());
        }
        
        System.out.println("=================== UPDATE =============================");
        s1.setName("Sam");
        dao.update(s1);

        students = dao.getAll();
        for (Student s : students) {
            System.out.println("Name: " + s.getName() + " | RollNumber: " + s.getRollNumber());
        }
        
        System.out.println("==================== DELETE ==========================");
        dao.delete(s1);

        students = dao.getAll();
        for (Student s : students) {
            System.out.println("Name: " + s.getName() + " | RollNumber: " + s.getRollNumber());
        }
        System.out.println("==================== END ==========================");
    }
}
```